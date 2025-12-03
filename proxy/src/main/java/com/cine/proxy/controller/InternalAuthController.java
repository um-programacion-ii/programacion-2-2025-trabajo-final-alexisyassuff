package com.cine.proxy.controller;

import com.cine.proxy.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints internos para gestionar token de la cátedra en desarrollo.
 *
 * Comportamiento seguro por defecto:
 * - POST /internal/catedra/token  { "token": "..." }  => guarda y persiste (dev helper)
 * - GET  /internal/catedra/token  => devuelve token en memoria SOLO si no se pasan credenciales
 *     - Si el cliente envía username/password como query params, NO se devuelve automáticamente el token.
 *       En su lugar se devuelve 401 (o 400) para evitar que la API actúe como un "auth bypass".
 * - DELETE /internal/catedra/token => borra token en memoria y Redis
 *
 * NOTA IMPORTANTE:
 * - Este controlador es únicamente una utilidad de desarrollo. NUNCA debe exponerse sin protección en producción.
 * - Para autenticación real, este endpoint no debe usarse: la app debe autenticar contra el servicio de
 *   autenticación / tokens real (por ejemplo el TokenController del backend), o el proxy debe delegar
 *   la validación al servicio de autenticación correspondiente.
 * - Si querés que este endpoint valide credenciales, debe delegar la validación a un UserService/AuthenticationManager
 *   y devolver 401 cuando las credenciales no sean correctas. Aquí dejamos explícito que no se acepta
 *   login mediante GET con query params (evita el "accept any password" que había).
 */
@RestController
@RequestMapping("/internal/catedra")
public class InternalAuthController {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthController.class);
    private final TokenService tokenService;

    public InternalAuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Guardar token en memoria/redis (dev helper).
     * Espera body JSON: { "token": "..." }
     */
    @PostMapping("/token")
    public ResponseEntity<?> setToken(@RequestBody java.util.Map<String,String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("token required");
        }
        tokenService.setTokenAndPersist(token);
        log.info("Token saved via internal endpoint (dev)");
        return ResponseEntity.ok(java.util.Map.of("saved", true));
    }

    /**
     * Obtener token en memoria.
     *
     * Seguridad/prevención: si se pasan query params username/password, NO se responde con el token.
     * Esto evita que un cliente que llame /internal/catedra/token?username=x&password=y
     * reciba el token indiscriminadamente.
     *
     * Uso recomendado en dev:
     * - GET /internal/catedra/token  -> devuelve token si existe (dev helper)
     * - Para autenticación en la app: usar el TokenController del backend o implementar delegación aquí.
     */
    @GetMapping("/token")
    public ResponseEntity<?> getToken(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {

        // Si el cliente intentó usar username/password como si fuera un endpoint de autenticación,
        // no exponemos el token ni lo auto-generamos: rechazamos la petición para evitar bypass.
        if ((username != null && !username.isBlank()) || (password != null && !password.isBlank())) {
            log.warn("Rejected token request with credentials in query params (potential auth attempt) usernamePresent={} passwordPresent={}",
                    (username != null && !username.isBlank()), (password != null && !password.isBlank()));
            // 401 Unauthorized es apropiado si se interpretara como intento de login,
            // o 400 Bad Request si preferís indicar que la ruta no acepta credenciales.
            return ResponseEntity.status(401).body("This internal endpoint does not perform authentication. Use the real auth service.");
        }

        String t = tokenService.getToken();
        if (t == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(java.util.Map.of("token", t));
    }

    /**
     * Borrar token en memoria/redis (dev helper).
     */
    @DeleteMapping("/token")
    public ResponseEntity<?> deleteToken() {
        tokenService.clearToken();
        log.info("Token cleared via internal endpoint (dev)");
        return ResponseEntity.ok(java.util.Map.of("deleted", true));
    }
}