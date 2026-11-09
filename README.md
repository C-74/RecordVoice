# RecordVoice — Application Android Native (Kotlin & Jetpack Compose)

Portage natif Android de la PWA **RecordPWA** (`https://warm-faun-5052e9.netlify.app/`), réalisé dans le cadre du cours **M2 Informatique — Android (De la PWA à l'app)**.  

**Auteur :** Yanis BELLAHCENE 

---

## 🚀 Construire et lancer le projet

### Prérequis
- **Android Studio** (Ladybug / Meerkat ou plus récent) avec **JDK 17+**
- Un appareil Android physique (recommandé pour tester le microphone, les raccourcis d'écran d'accueil et l'App Link) ou un émulateur Android (**API 26 minimum**, **API 33+** pour la permission runtime `POST_NOTIFICATIONS`).

### En ligne de commande (ou via le bouton Run ▶ d'Android Studio)
```powershell
# 1. Compiler l'APK de debug (génère aussi le schéma Room dans app/schemas/)
./gradlew :app:assembleDebug

# 2. Installer sur le téléphone connecté en USB / Wi-Fi
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏗️ Architecture et Choix Techniques

1. **Les couches (Chapitre 1 — Single Source of Truth) :**
   - **UI (`RecordVoiceScreen.kt`)** : Jetpack Compose (Material 3), 100 % déclarative avec hissage d'état (`StateFlow` collecté via `collectAsStateWithLifecycle()`). S'adapte automatiquement en **2 onglets** en mode Portrait et **2 colonnes côte à côte** en mode Paysage/Tablette.
   - **ViewModel (`RecorderViewModel.kt`)** : Gère l'enregistrement `MediaRecorder`, le lecteur `MediaPlayer` et survit aux rotations d'écran et à la mort du processus grâce à `SavedStateHandle`.
   - **Repository (`RecordingsRepository.kt`)** : Source de vérité unique qui coordonne Room, le stockage de fichiers audio (`filesDir/recordings/`), les raccourcis système et `WorkManager`.

2. **Les données sur disque (Chapitre 2 — Room + Migration + DataStore) :**
   - **Room Database (`AppDatabase.kt`)** :
     - Table `recordings` (`RecordingEntity`) : métadonnées des notes vocales et état `synced` (le binaire audio `.m4a`/`.wav` reste sur le système de fichiers interne pour éviter de saturer les `CursorWindow` SQLite).
     - Table `analytics_events` (`AnalyticsEventEntity`) : file d'attente hors-ligne (Outbox) des événements Umami.
     - **Migration explicite (`MIGRATION_1_2`)** avec `exportSchema = true` (schémas versionnés dans `app/schemas/`), **sans** `fallbackToDestructiveMigration`.
   - **Jetpack DataStore (`UserPreferencesRepository.kt`)** : Persiste le dernier onglet ouvert, l'état des permissions, le timestamp du `first_open` et les compteurs de continuité de session (`J+0`, `J+1`, `J+2`, `J+5`...).

3. **L'arrière-plan & Mode Avion (Chapitre 4 — Outbox WorkManager) :**
   - `SyncOutboxWorker` (`CoroutineWorker`) est planifié en tâche unique (`ExistingWorkPolicy.REPLACE`) avec la contrainte `NetworkType.CONNECTED`.
   - En **Mode Avion**, les notes vocales (`synced = false`) et les événements Analytics restent en attente dans Room. Dès que le réseau revient (même si l'application est fermée), l'OS exécute `SyncOutboxWorker` pour synchroniser les notes et vider la file Umami.

4. **Les notifications (Chapitre 5) :**
   - Canal `record_voice_channel` créé au démarrage.
   - Une notification système avec icône dédiée (`ic_notif_mic.xml`) est affichée lorsque `SyncOutboxWorker` termine la synchronisation Cloud en arrière-plan. Son clic (`PendingIntent`) ouvre directement la note synchronisée dans l'onglet **Notes**.

5. **Les permissions runtime (Chapitre 6) :**
   - `RECORD_AUDIO` est demandée **en contexte** uniquement au clic sur *« Enregistrer »*.
   - Gère les 3 états : accordée, explication pédagogique (`shouldShowRequestPermissionRationale`), et **refus définitif** avec un bouton redirigeant vers les réglages système (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`). L'application reste utilisable sans la permission grâce au générateur de mémo de démonstration (440 Hz).

6. **L'App Link (Jour 4) :**
   - `<intent-filter android:autoVerify="true">` configuré dans `AndroidManifest.xml` pour `https://warm-faun-5052e9.netlify.app` et vérifié via `/.well-known/assetlinks.json`.
   - Tester via ADB :
     ```powershell
     adb shell am start -a android.intent.action.VIEW -d "https://warm-faun-5052e9.netlify.app/"
     ```

7. **Fonctionnalité native impossible en PWA : Raccourcis Android (`ShortcutManagerCompat`) :**
   - **Raccourci statique (`res/xml/shortcuts.xml`)** : *« Enregistrer »* au clic long sur l'icône lance immédiatement la prise de son.
   - **Raccourcis dynamiques (`ShortcutHelper.kt`)** : Les **3 dernières notes vocales** apparaissent au clic long sur l'icône de l'app et redirigent directement vers la note dans l'onglet *Notes* en lançant sa lecture.
   - **Raccourcis épinglés (`requestPinShortcut`)** : Le bouton *« Épingler »* sur chaque carte permet d'ajouter une note vocale précise sur l'écran d'accueil Android.

8. **Mesure d'audience (Annexe 4 — Umami Analytics + Outbox hors-ligne) :**
   - Envoi vers `POST https://cloud.umami.is/api/send` (Website ID : `bd60c11f-35a5-4e1b-832c-d833f305f760`) :
     - `first_open` : première ouverture de l'application.
     - `note_created` : création d'une note vocale (`is_first: 0/1`, `offline: 0/1`, `duration_sec`).
     - `session_start` : retour de l'utilisateur plusieurs fois par jour (`sessions_today`) et continuité sur les jours (`retention_day = J+0, J+1, J+2, J+5...`).
     - `shortcut_user` : épinglage d'une note sur l'écran d'accueil ou ouverture via raccourci natif (`type`, `action`, `result`).
     - Suivi manuel des écrans Compose (`/recorder` et `/notes`).
   - Observer les événements en direct dans Logcat :
     ```powershell
     adb logcat -s RecordVoiceAnalytics
     ```
