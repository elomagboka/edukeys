/**
 * Contexte multi-établissement (T-05) : résolution de l'établissement
 * courant, porteur de contexte pour les traitements sans requête HTTP
 * (asynchrone, planifié), et armement du filtre Hibernate
 * {@code filtreEtablissement} sur chaque session.
 *
 * <p>{@code @FilterDef} est déclaré ici, au niveau du package, plutôt que sur
 * une entité précise : c'est le point neutre du socle {@code common}, visible
 * par toutes les entités {@link tg.novadigital.edukeys.common.domain.EntiteEtablissement}
 * sans dépendance vers un module métier. Spring scanne les classes
 * {@code package-info} au même titre que les classes {@code @Entity} lors de
 * la construction du modèle JPA (voir {@code PersistenceManagedTypesScanner}),
 * ce qui rend ce {@code @FilterDef} visible d'Hibernate au démarrage.</p>
 */
@org.hibernate.annotations.FilterDef(
        name = "filtreEtablissement",
        parameters = @org.hibernate.annotations.ParamDef(name = "etablissementId", type = java.util.UUID.class))
package tg.novadigital.edukeys.common.multietablissement;
