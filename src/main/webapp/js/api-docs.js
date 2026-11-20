/*
 * Documentacion interactiva de la API (Swagger UI) sobre openapi.json.
 *
 * Si hay una sesion abierta en esta pestana, el token JWT se agrega solo a
 * cada pedido de "Try it out"; si no, se puede pegar con el boton Authorize.
 * Es un script clasico (no modulo) porque Swagger UI expone un global.
 */
(function () {
    'use strict';

    var contexto = window.location.pathname.replace(/\/api-docs(\.html)?$/, '');

    function tokenDeLaSesion() {
        try {
            return window.sessionStorage.getItem('mediconecta.token');
        } catch (e) {
            return null;
        }
    }

    window.SwaggerUIBundle({
        url: contexto + '/openapi.json',
        dom_id: '#swagger-ui',
        deepLinking: true,
        // No mandar el documento a validator.swagger.io (fuera de la CSP y de la privacidad).
        validatorUrl: null,
        requestInterceptor: function (pedido) {
            var token = tokenDeLaSesion();
            if (token && !pedido.headers.Authorization) {
                pedido.headers.Authorization = 'Bearer ' + token;
            }
            return pedido;
        }
    });
}());
