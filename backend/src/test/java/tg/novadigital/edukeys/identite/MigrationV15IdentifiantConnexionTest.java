package tg.novadigital.edukeys.identite;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * US-08a : V15 appliquée sur des données antérieures (schéma V14 déjà peuplé),
 * sur une base vierge dédiée. Vérifie le rattrapage de l'identifiant de
 * connexion (email normalisé), l'email devenu nullable et l'index partiel.
 */
class MigrationV15IdentifiantConnexionTest {

    @Test
    void v15_rattrapeLIdentifiantDesComptesExistants_etAutoriseLesComptesSansEmail() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))) {
            pg.start();
            Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                    .locations("classpath:db/migration").target("14").load().migrate();

            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                    Statement s = c.createStatement()) {
                s.execute("insert into utilisateurs (id, email, mot_de_passe_hache, nom_complet, super_admin, actif, date_creation, date_modification) "
                        + "values ('01977000-0000-7000-8000-0000000009a1', 'Ancien.Utilisateur@Edukeys.TG', 'x', 'Ancien', false, true, now(), now())");

                Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                        .locations("classpath:db/migration").target("15").load().migrate();

                try (ResultSet rs = s.executeQuery(
                        "select identifiant_connexion from utilisateurs where id = '01977000-0000-7000-8000-0000000009a1'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("ancien.utilisateur@edukeys.tg");
                }
                // Email nullable : un compte sans email est désormais insérable.
                s.execute("insert into utilisateurs (id, email, identifiant_connexion, mot_de_passe_hache, nom_complet, super_admin, actif, date_creation, date_modification) "
                        + "values ('01977000-0000-7000-8000-0000000009a2', null, 'mat-1', 'x', 'Eleve', false, true, now(), now())");
                // Rétrocompatibilité (ADR-0004/0007) : l'ancien code, qui ignore la colonne,
                // insère sans identifiant_connexion ; le trigger le remplit depuis l'email.
                s.execute("insert into utilisateurs (id, email, mot_de_passe_hache, nom_complet, super_admin, actif, date_creation, date_modification) "
                        + "values ('01977000-0000-7000-8000-0000000009a3', ' Ancien.Code@Edukeys.TG ', 'x', 'Ancien code', false, true, now(), now())");
                try (ResultSet rs = s.executeQuery(
                        "select identifiant_connexion from utilisateurs where id = '01977000-0000-7000-8000-0000000009a3'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("ancien.code@edukeys.tg");
                }
                // Une valeur fournie (nouveau code) n'est jamais écrasée par le trigger.
                try (ResultSet rs = s.executeQuery(
                        "select identifiant_connexion from utilisateurs where id = '01977000-0000-7000-8000-0000000009a2'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("mat-1");
                }
                // La colonne reste nullable dans cette version (la contrainte NOT NULL est une migration ultérieure).
                try (ResultSet rs = s.executeQuery(
                        "select is_nullable from information_schema.columns "
                                + "where table_name = 'utilisateurs' and column_name = 'identifiant_connexion'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("YES");
                }
                try (ResultSet rs = s.executeQuery(
                        "select indexdef from pg_indexes where indexname = 'uk_utilisateurs_identifiant_connexion_actif'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).containsIgnoringCase("WHERE").containsIgnoringCase("actif");
                }
            }
        }
    }
}
