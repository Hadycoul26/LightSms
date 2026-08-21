package com.example.lightsms

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD

/**
 * Serveur HTTP local, heberge par [LightService].
 *
 * Sert a piloter le telephone depuis un appareil connecte a son point d'acces,
 * sans passer par le SMS.
 *
 * Limite inherente : ce serveur ne peut pas ALLUMER le point d'acces, puisqu'il
 * faut deja y etre connecte pour l'atteindre. L'amorcage reste le SMS.
 */
class WebServer(
    private val context: Context,
    port: Int
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            val token = session.parameters["k"]?.firstOrNull()

            if (!Prefs.isTokenValid(context, token)) {
                return html(Response.Status.UNAUTHORIZED, loginPage())
            }

            when (session.uri) {
                "/cmd" -> runCommand(session)
                "/state" -> text(stateLine())
                else -> html(Response.Status.OK, mainPage(token!!))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Requete en erreur", e)
            html(Response.Status.INTERNAL_ERROR, "<p>Erreur : " + escape(e.toString()) + "</p>")
        }
    }

    private fun runCommand(session: IHTTPSession): Response {
        val name = session.parameters["c"]?.firstOrNull()
        val command = try {
            if (name == null) null else Command.valueOf(name)
        } catch (e: IllegalArgumentException) {
            null
        } ?: return html(Response.Status.BAD_REQUEST, "<p>Commande inconnue.</p>")

        val result = CommandExecutor.execute(context, command)

        EventLog.add(
            context, "web", command.name, "", command.name,
            if (result.ok) "OK - " + result.detail else "ECHEC - " + result.detail
        )
        Prefs.setLastEvent(
            context,
            (if (result.ok) "" else "Echec : ") + result.detail + " (via le web)"
        )

        return text((if (result.ok) "OK " else "ECHEC ") + result.detail)
    }

    private fun stateLine(): String {
        val torch = if (Prefs.isTorchOn(context)) "allumee" else "eteinte"
        val shizuku = when (ShizukuShell.state()) {
            ShizukuState.PRET -> "pret"
            ShizukuState.NON_AUTORISE -> "permission a accorder"
            ShizukuState.ABSENT -> "non lance"
        }
        return "Lampe : $torch | Shizuku : $shizuku"
    }

    // --- Pages -----------------------------------------------------------

    private fun loginPage(): String = PAGE_SHELL.replace(
        "%BODY%",
        """
        <h1>Light SMS</h1>
        <p class="muted">Entrez la cle affichee dans l'application.</p>
        <form method="get" action="/">
          <input name="k" placeholder="cle" autocapitalize="characters" autofocus>
          <button type="submit">Entrer</button>
        </form>
        """.trimIndent()
    )

    private fun mainPage(token: String): String {
        val buttons = listOf(
            Triple(Command.DATA_ON, "Données mobiles ON", "primary"),
            Triple(Command.DATA_OFF, "Données mobiles OFF", ""),
            Triple(Command.LIGHT_ON, "Lampe ON", ""),
            Triple(Command.LIGHT_OFF, "Lampe OFF", ""),
            Triple(Command.HOTSPOT_OFF, "Couper le point d'accès", "danger")
        ).joinToString("\n") { (command, label, style) ->
            """<button class="$style" onclick="send('${command.name}')">$label</button>"""
        }

        val body = """
        <h1>Light SMS</h1>
        <p id="state" class="muted">${escape(stateLine())}</p>
        $buttons
        <p class="warn">Couper le point d'accès vous déconnectera de cette page,
        et seul un SMS pourra le rallumer.</p>
        <pre id="out"></pre>
        <script>
          var K = ${quote(token)};
          function send(c) {
            if (c === 'HOTSPOT_OFF' &&
                !confirm("Couper le point d'accès ? Vous perdrez cette page.")) return;
            document.getElementById('out').textContent = '...';
            fetch('/cmd?k=' + K + '&c=' + c)
              .then(function (r) { return r.text(); })
              .then(function (t) { document.getElementById('out').textContent = t; refresh(); })
              .catch(function (e) { document.getElementById('out').textContent = 'Injoignable'; });
          }
          function refresh() {
            fetch('/state?k=' + K)
              .then(function (r) { return r.text(); })
              .then(function (t) { document.getElementById('state').textContent = t; })
              .catch(function (e) {});
          }
          setInterval(refresh, 5000);
        </script>
        """.trimIndent()

        return PAGE_SHELL.replace("%BODY%", body)
    }

    // --- Utilitaires -----------------------------------------------------

    private fun html(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "text/html; charset=utf-8", body)

    private fun text(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", body)

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun quote(s: String) = "\"" + s.replace("\"", "") + "\""

    private companion object {
        const val TAG = "WebServer"

        val PAGE_SHELL = """
        <!doctype html><html lang="fr"><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Light SMS</title>
        <style>
          :root { color-scheme: light dark; }
          body { font-family: system-ui, sans-serif; margin: 0; padding: 24px;
                 max-width: 480px; margin-inline: auto; line-height: 1.5; }
          h1 { font-size: 1.4rem; margin: 0 0 4px; }
          .muted { opacity: .7; font-size: .9rem; margin-top: 0; }
          .warn { opacity: .7; font-size: .8rem; }
          button { display: block; width: 100%; padding: 16px; margin: 8px 0;
                   font-size: 1rem; border-radius: 12px; border: 1px solid currentColor;
                   background: transparent; color: inherit; cursor: pointer; }
          button.primary { background: #2e7d32; color: #fff; border-color: #2e7d32; }
          button.danger { border-color: #c62828; color: #c62828; }
          input { width: 100%; padding: 14px; font-size: 1rem; border-radius: 12px;
                  border: 1px solid currentColor; background: transparent; color: inherit;
                  box-sizing: border-box; }
          pre { white-space: pre-wrap; font-size: .85rem; opacity: .8; }
        </style></head><body>
        %BODY%
        </body></html>
        """.trimIndent()
    }
}
