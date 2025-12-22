// package com.cine.backend.controller;
// import com.cine.backend.model.ExternalToken;
// import com.cine.backend.service.TokenService;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.Optional;

// @RestController
// @RequestMapping("/tokens")
// public class TokenController {

//     private final TokenService tokenService;

//     public TokenController(TokenService tokenService) {
//         this.tokenService = tokenService;
//     }

//     @PostMapping
//     public ResponseEntity<ExternalToken> save(@RequestBody ExternalToken body) {
//         if (body == null || body.getServiceName() == null || body.getToken() == null) {
//             return ResponseEntity.badRequest().build();
//         }
//         ExternalToken saved = tokenService.saveToken(body.getToken(), body.getServiceName());
//         return ResponseEntity.ok(saved);
//     }

//     @GetMapping("/latest")
//     public ResponseEntity<ExternalToken> latest(@RequestParam("serviceName") String serviceName) {
//         Optional<ExternalToken> t = tokenService.getLatestToken(serviceName);
//         return t.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
//     }
// }