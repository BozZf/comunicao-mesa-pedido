package br.com.mesapedido.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {
  @Bean
  UserDetailsService users(
      @Value("${app.cashier-user}") String user,
      @Value("${app.cashier-password}") String password) {
    if (password.length() < 12)
      throw new IllegalStateException("CASHIER_PASSWORD precisa ter ao menos 12 caracteres");
    return new InMemoryUserDetailsManager(
        User.withUsername(user)
            .password(new BCryptPasswordEncoder().encode(password))
            .roles("CASHIER")
            .build());
  }

  @Bean
  org.springframework.security.crypto.password.PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/cashier/**",
                        "/caixa.html",
                        "/caixa.js",
                        "/comprovante.html",
                        "/comprovante.js")
                    .hasRole("CASHIER")
                    .anyRequest()
                    .permitAll())
        .httpBasic(withDefaults())
        .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src"
                                + " 'self' data:; connect-src 'self'; frame-ancestors 'none';"
                                + " base-uri 'self'; form-action 'self'")))
        .build();
  }
}
