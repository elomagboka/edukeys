package tg.novadigital.edukeys.identite.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Sécurisation par défaut d'Edukeys : tout est interdit sauf explicitement
 * déclaré ouvert (arbitrage T-04). Authentification JWT sans état (l'access
 * token n'est jamais vérifié en base — arbitrage n°3), CORS activé pour le
 * futur frontend (T-11).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final PermissionResolver permissionResolver;
    private final List<String> originesAutorisees;
    private final Environment environment;

    public SecurityConfig(
            JwtService jwtService,
            PermissionResolver permissionResolver,
            @Value("${edukeys.securite.cors.origines-autorisees}") List<String> originesAutorisees,
            Environment environment) {
        this.jwtService = jwtService;
        this.permissionResolver = permissionResolver;
        this.originesAutorisees = originesAutorisees;
        this.environment = environment;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(sourceCors()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpStatus.FORBIDDEN.value())))
                .authorizeHttpRequests(authorize -> {
                    authorize
                            // Seuls login/refresh sont ouverts : /etablissement-actif exige une
                            // authentification (correction T-04, lot 2 n°8), le filtre la refuse
                            // en 401 avant même d'atteindre le @PreAuthorize du contrôleur.
                            .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                            .requestMatchers("/actuator/health").permitAll();
                    // Surface de démonstration/test de T-03 (voir DemoEntiteController,
                    // déjà @Profile({"local","test"})) : la règle elle-même n'existe qu'en
                    // local/test, pour qu'en production le refus par défaut couvre tout
                    // futur endpoint /internal/**, sans dépendre d'une convention de nommage.
                    if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
                        authorize.requestMatchers("/internal/**").permitAll();
                    }
                    authorize
                            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, permissionResolver), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource sourceCors() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(originesAutorisees);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
