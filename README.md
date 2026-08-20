# Light SMS

App Android qui écoute les SMS en arrière-plan et pilote la lampe torche.

| SMS reçu     | Effet                  |
|--------------|------------------------|
| `light on`   | Allume la lampe torche |
| `light off`  | Éteint la lampe torche |

Le texte est normalisé avant analyse : `Light ON!`, `light-off`, `LIGHT   On`
fonctionnent aussi. La commande peut être n'importe où dans le message.

## Compiler (sans Android Studio)

Le build tourne sur **GitHub Actions** : rien à installer, rien sur ton disque.
Le workflow est dans `.github/workflows/build.yml`.

1. Créer un repo **vide** sur <https://github.com/new> (nom : `LightSms`,
   public ou privé, **sans** README ni .gitignore).
2. Depuis le dossier du projet :

   ```
   git remote add origin https://github.com/<TON_PSEUDO>/LightSms.git
   git push -u origin main
   ```

3. Sur GitHub, onglet **Actions** → le build « Build APK » démarre tout seul
   (~3-4 min). S'il ne démarre pas : *Build APK* → *Run workflow*.
4. Cliquer sur le run terminé → section **Artifacts** → télécharger
   `LightSms-debug-apk`. C'est un `.zip` : le décompresser sur le PC pour
   récupérer `app-debug.apk`.
5. Transférer l'APK sur le téléphone (USB, Bluetooth, Drive, WhatsApp...),
   l'ouvrir, et autoriser l'installation depuis une source inconnue.

L'APK est signé avec la clé de debug : installable directement, mais pas
publiable sur un store.

Versions utilisées : AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, `compileSdk` 35,
`minSdk` 26 (Android 8.0+).

## Utiliser

1. Ouvrir l'app **au moins une fois** après l'installation. Tant qu'une app n'a
   jamais été lancée, Android ne lui livre aucun broadcast — donc ni les SMS ni
   le démarrage automatique ne marcheraient.
2. Activer l'interrupteur *Écoute des SMS* et accorder les permissions
   (SMS + notifications).
3. Une notification permanente confirme que l'écoute est active.
4. Envoyer `light on` / `light off` depuis un autre téléphone.

Après un redémarrage du téléphone, le service repart tout seul si l'interrupteur
était activé.

### Important : optimisation de la batterie

Sur Xiaomi, Huawei, Oppo, Vivo, Samsung, le système tue les apps en arrière-plan
de façon agressive. Le bouton *Optimisation de la batterie* dans l'app ouvre le
réglage : mettre Light SMS en « non optimisée » / « autoriser en arrière-plan ».
Sans ça l'écoute finit par s'arrêter.

## Comment ça marche

| Fichier | Rôle |
|---|---|
| `SmsReceiver.kt` | Reçoit `SMS_RECEIVED`, recolle les SMS multi-parties, applique la commande |
| `Command.kt` | Normalisation du texte + reconnaissance `light on` / `light off` |
| `TorchController.kt` | `CameraManager.setTorchMode()` sur la première caméra avec flash |
| `BootReceiver.kt` | Relance le service après un reboot ou une mise à jour de l'app |
| `LightService.kt` | Service de premier plan : notification d'état, garde l'app vivante |
| `MainActivity.kt` | Interrupteur, permissions, boutons de test, raccourcis réglages |
| `Prefs.kt` | SharedPreferences : activé, état lampe, dernier événement |

Le récepteur SMS est déclaré dans le manifeste, donc Android réveille l'app même
si elle est fermée. `SMS_RECEIVED` et `BOOT_COMPLETED` font partie des broadcasts
exemptés des limites d'arrière-plan d'Android 8+, le service n'est là que pour la
robustesse face aux surcouches constructeur.

## Limites de cette v1

- **N'importe qui connaissant ton numéro peut allumer ta lampe.** Il n'y a pas de
  filtre d'expéditeur ni de mot de passe — c'était le choix « v1 simple ».
- Pas d'accusé de réception par SMS.
- `setTorchMode()` échoue si l'appareil photo est déjà utilisé par une autre app.
- L'app ne passe pas la validation Google Play : la permission `RECEIVE_SMS` y est
  réservée aux apps de messagerie par défaut. Installation par sideload / APK.

## Pistes v2

- Liste blanche de numéros autorisés (≈15 lignes dans `SmsReceiver`).
- Mot-clé secret : `light on 4821`.
- Réponse SMS de confirmation.
- Autres commandes : `light blink`, `light status`, sonnerie, localisation.
