package local.tv.hue;
import android.content.Context;
final class DirectText {
 private DirectText(){}
 static String get(Context c,String key){
  String l=c.getResources().getConfiguration().locale.getLanguage();int i="cs".equals(l)?0:"de".equals(l)?2:"fr".equals(l)?3:"es".equals(l)?4:"pl".equals(l)?5:1;
  String[] a;
  switch(key){
   case "direct_status":a=new String[]{"Přímá synchronizace","Direct synchronization","Direkte Synchronisierung","Synchronisation directe","Sincronización directa","Synchronizacja bezpośrednia"};break;
   case "direct_running":a=new String[]{"běží","running","läuft","active","activa","działa"};break;
   case "direct_stopped":a=new String[]{"neběží","not running","läuft nicht","inactive","inactiva","nie działa"};break;
   case "normal_state":a=new String[]{"normální stav","normal state","Normalzustand","état normal","estado normal","stan normalny"};break;
   case "bridge_ip":a=new String[]{"IP adresa můstku","Bridge IP address","Bridge-IP-Adresse","Adresse IP du pont","Dirección IP del puente","Adres IP mostka"};break;
   case "temporary_proxy":a=new String[]{"Dočasná proxy","Temporary proxy","Temporärer Proxy","Proxy temporaire","Proxy temporal","Tymczasowy serwer proxy"};break;
   case "receiver_status":a=new String[]{"Přijímač","Receiver","Empfänger","Récepteur","Receptor","Odbiornik"};break;
   case "receiver_running":a=new String[]{"běží (jen při instalaci)","running (setup only)","läuft (nur bei Einrichtung)","actif (installation uniquement)","activo (solo durante la instalación)","działa (tylko podczas konfiguracji)"};break;
   case "receiver_stopped":a=new String[]{"neběží (normální stav)","stopped (normal state)","gestoppt (Normalzustand)","arrêté (état normal)","detenido (estado normal)","nie działa (stan normalny)"};break;
   case "air_status":a=new String[]{"Ambilight Air","Ambilight Air","Ambilight Air","Ambilight Air","Ambilight Air","Ambilight Air"};break;
   case "air_paired":a=new String[]{"spárováno","paired","gekoppelt","associé","emparejado","sparowano"};break;
   case "air_receiving":a=new String[]{"přijímá barvy","receiving colours","empfängt Farben","réception des couleurs","recibiendo colores","odbiera kolory"};break;
   case "direct_config_required":a=new String[]{
    "Pro vybraný můstek otevřete Nastavení světel, vyberte oblast Entertainment a uložte nastavení světel. Potom spusťte přímou synchronizaci.",
    "Open Light settings for the selected Bridge, choose an Entertainment area and save the light settings. Then start direct synchronization.",
    "Öffnen Sie die Lampeneinstellungen der gewählten Bridge, wählen Sie einen Entertainment-Bereich und speichern Sie die Lampeneinstellungen. Starten Sie dann die direkte Synchronisierung.",
    "Ouvrez les réglages des lumières du pont sélectionné, choisissez une zone de divertissement et enregistrez les réglages. Lancez ensuite la synchronisation directe.",
    "Abre la configuración de luces del puente seleccionado, elige un área de entretenimiento y guarda la configuración. A continuación, inicia la sincronización directa.",
    "Otwórz ustawienia świateł wybranego mostka, wybierz obszar rozrywki i zapisz ustawienia. Następnie uruchom synchronizację bezpośrednią."
   };break;
   case "enabled":a=new String[]{"povoleno","enabled","aktiviert","activé","activado","włączony"};break;
   case "disabled":a=new String[]{"nenastaveno","not configured","nicht eingerichtet","non configuré","sin configurar","nieskonfigurowany"};break;
   case "running_short":a=new String[]{"spuštěna","running","aktiv","actif","activo","uruchomiony"};break;
   case "inactive":a=new String[]{"neaktivní","inactive","inaktiv","inactif","inactivo","nieaktywny"};break;
   case "air_setup_starting":a=new String[]{"Spouštím přijímač Ambilight Air…","Starting the Ambilight Air receiver…","Ambilight Air-Empfänger wird gestartet…","Démarrage du récepteur Ambilight Air…","Iniciando el receptor Ambilight Air…","Uruchamianie odbiornika Ambilight Air…"};break;
   case "discovery_failed":a=new String[]{"Vyhledávání můstku selhalo.","Bridge discovery failed.","Die Bridge-Suche ist fehlgeschlagen.","La recherche du pont a échoué.","No se pudo encontrar el puente.","Wyszukiwanie mostka nie powiodło się."};break;
   case "enter_ipv4":a=new String[]{"Zadejte adresu IPv4 můstku.","Enter the Bridge’s IPv4 address.","Geben Sie die IPv4-Adresse der Bridge ein.","Saisissez l’adresse IPv4 du pont.","Introduzca la dirección IPv4 del puente.","Podaj adres IPv4 mostka."};break;
   case "pair_prompt":a=new String[]{"Stiskněte tlačítko na můstku a potvrďte jeho certifikát.","Press the Bridge button and confirm its certificate.","Drücken Sie die Taste an der Bridge und bestätigen Sie ihr Zertifikat.","Appuyez sur le bouton du pont et confirmez son certificat.","Pulse el botón del puente y confirme su certificado.","Naciśnij przycisk mostka i potwierdź jego certyfikat."};break;
   case "pair_done":a=new String[]{"Můstek je spárovaný. Načtěte oblasti.","Bridge paired. Load the areas.","Bridge gekoppelt. Laden Sie die Bereiche.","Pont associé. Chargez les zones.","Puente vinculado. Cargue las zonas.","Mostek sparowany. Wczytaj strefy."};break;
   case "pair_failed":a=new String[]{"Párování selhalo. Zkontrolujte adresu, tlačítko a certifikát můstku.","Pairing failed. Check the Bridge address, button and certificate.","Kopplung fehlgeschlagen. Prüfen Sie Adresse, Taste und Zertifikat der Bridge.","Échec de l’association. Vérifiez l’adresse, le bouton et le certificat du pont.","No se pudo vincular. Compruebe la dirección, el botón y el certificado del puente.","Parowanie nie powiodło się. Sprawdź adres, przycisk i certyfikat mostka."};break;
   case "no_areas":a=new String[]{"Můstek nemá žádné oblasti Entertainment.","The Bridge has no Entertainment areas.","Die Bridge hat keine Entertainment-Bereiche.","Le pont ne contient aucune zone Entertainment.","El puente no tiene zonas de entretenimiento.","Mostek nie ma stref Entertainment."};break;
   case "save_failed":a=new String[]{"Nastavení se nepodařilo uložit.","Could not save the settings.","Die Einstellungen konnten nicht gespeichert werden.","Impossible d’enregistrer les réglages.","No se pudieron guardar los ajustes.","Nie udało się zapisać ustawień."};break;
   case "area_active":a=new String[]{"aktivní","active","aktiv","active","activa","aktywna"};break;
   case "area_inactive":a=new String[]{"neaktivní","inactive","inaktiv","inactive","inactiva","nieaktywna"};break;
   case "close_app":a=new String[]{"Zavřít aplikaci","Close app","App schließen","Fermer l’application","Cerrar la aplicación","Zamknij aplikację"};break;
   case "restoring":a=new String[]{"Obnovuji předchozí stav světel…","Restoring the previous light state…","Der vorherige Zustand der Lampen wird wiederhergestellt…","Restauration de l’état précédent des lumières…","Restaurando el estado anterior de las luces…","Przywracanie poprzedniego stanu świateł…"};break;
   case "notification_channel":a=new String[]{"Synchronizace Ambilight","Ambilight synchronization","Ambilight-Synchronisierung","Synchronisation Ambilight","Sincronización de Ambilight","Synchronizacja Ambilight"};break;
   case "sync_failed":a=new String[]{"Přímá synchronizace selhala. Podrobnosti jsou v diagnostice.","Direct synchronization failed. See the diagnostic log for details.","Direkte Synchronisierung fehlgeschlagen. Details stehen im Diagnoseprotokoll.","Échec de la synchronisation directe. Consultez le journal de diagnostic.","Falló la sincronización directa. Consulte el registro de diagnóstico.","Synchronizacja bezpośrednia nie powiodła się. Szczegóły znajdują się w dzienniku diagnostycznym."};break;
   case "finish_air_setup":a=new String[]{"Nejprve dokončete nebo ukončete párování Ambilight Air.","Finish or close Ambilight Air pairing first.","Schließen oder beenden Sie zuerst die Ambilight Air-Kopplung.","Terminez ou fermez d’abord l’association Ambilight Air.","Primero termine o cierre la vinculación de Ambilight Air.","Najpierw zakończ lub zamknij parowanie Ambilight Air."};break;
   case "air_remote_permission":a=new String[]{"Povolit opravu ovladače v průvodci","Enable remote control assistance in the wizard","Fernbedienungshilfe im Assistenten aktivieren","Activer l’aide à la télécommande dans l’assistant","Activar la ayuda del mando en el asistente","Włącz obsługę pilota w kreatorze"};break;
   case "air_remote_hint":a=new String[]{
    "Použijte při nastavení Air, pokud v průvodci Philips nereagují šipky nebo tlačítko OK.",
    "Use during Air setup if the arrow buttons or OK do not respond in the Philips wizard.",
    "Für die Air-Einrichtung, wenn die Pfeiltasten oder OK im Philips-Assistenten nicht reagieren.",
    "À utiliser pendant la configuration d’Air si les flèches ou la touche OK ne répondent pas dans l’assistant Philips.",
    "Úsalo al configurar Air si las flechas o el botón OK no responden en el asistente de Philips.",
    "Użyj podczas konfiguracji Air, jeśli strzałki lub przycisk OK nie działają w kreatorze Philips."
   };break;
   case "air_setup":a=new String[]{"Spárovat Ambilight Air","Pair Ambilight Air","Ambilight Air koppeln","Associer Ambilight Air","Vincular Ambilight Air","Sparuj Ambilight Air"};break;
   case "air_network":a=new String[]{"Otevřít nastavení sítě","Open network settings","Netzwerkeinstellungen öffnen","Ouvrir les réglages réseau","Abrir los ajustes de red","Otwórz ustawienia sieci"};break;
   case "air_setup_ready":a=new String[]{"Přijímač je připraven. Otevřete nastavení Ambilight Air, zvolte Konfigurace a spusťte vyhledávání.","Receiver ready. Open Ambilight Air settings, choose Configuration and start discovery.","Empfänger bereit. Öffnen Sie die Ambilight-Air-Einstellungen, wählen Sie Konfiguration und starten Sie die Suche.","Récepteur prêt. Ouvrez les réglages Ambilight Air, sélectionnez Configuration et lancez la recherche.","Receptor listo. Abre los ajustes de Ambilight Air, elige Configuración e inicia la búsqueda.","Odbiornik jest gotowy. Otwórz ustawienia Ambilight Air, wybierz Konfiguracja i rozpocznij wyszukiwanie."};break;
   case "air_setup_stop":a=new String[]{"Ukončit přijímač","Stop receiver","Empfänger stoppen","Arrêter le récepteur","Detener el receptor","Zatrzymaj odbiornik"};break;
   case "air_setup_failed":a=new String[]{"Přijímač nebo systémové nastavení Air nejsou dostupné.","The receiver or system Air settings are unavailable.","Der Empfänger oder die Air-Systemeinstellungen sind nicht verfügbar.","Le récepteur ou les réglages système d’Air sont indisponibles.","El receptor o los ajustes del sistema de Air no están disponibles.","Odbiornik lub systemowe ustawienia Air są niedostępne."};break;
   case "air_source":a=new String[]{"Zdroj barev: Ambilight Air","Colour source: Ambilight Air","Farbquelle: Ambilight Air","Source des couleurs : Ambilight Air","Fuente de color: Ambilight Air","Źródło kolorów: Ambilight Air"};break;
   case "air_settings":a=new String[]{"Otevřít nastavení Ambilight Air","Open Ambilight Air settings","Ambilight Air-Einstellungen öffnen","Ouvrir les réglages d’Ambilight Air","Abrir los ajustes de Ambilight Air","Otwórz ustawienia Ambilight Air"};break;
   case "air_hint":a=new String[]{
    "Po prvním nastavení se aplikace při spuštění přímé synchronizace pokusí Air zapnout. Průvodce Philips se může krátce zobrazit.",
    "After setup, the app tries to enable Air when you start direct synchronization. The Philips wizard may appear briefly.",
    "Nach der Einrichtung versucht die App, Air beim Start der direkten Synchronisierung zu aktivieren. Der Philips-Assistent kann kurz erscheinen.",
    "Après la configuration, l’application essaie d’activer Air au démarrage de la synchronisation directe. L’assistant Philips peut s’afficher brièvement.",
    "Tras la configuración, la aplicación intenta activar Air al iniciar la sincronización directa. El asistente de Philips puede aparecer brevemente.",
    "Po konfiguracji aplikacja próbuje włączyć Air przy uruchamianiu synchronizacji bezpośredniej. Kreator Philips może pojawić się na chwilę."
   };break;
   case "air_unavailable":a=new String[]{"TV neposílá data Ambilight Air. Zkontrolujte jeho nastavení a zapnutí.","The TV is not sending Ambilight Air data. Check that Air is configured and enabled.","Der Fernseher sendet keine Ambilight Air-Daten. Prüfen Sie Einrichtung und Aktivierung.","Le téléviseur n’envoie pas de données Ambilight Air. Vérifiez sa configuration et son activation.","El televisor no envía datos de Ambilight Air. Compruebe que esté configurado y activado.","Telewizor nie wysyła danych Ambilight Air. Sprawdź konfigurację i włączenie."};break;
   case "title":a=new String[]{"Přímá synchronizace – test","Direct synchronization – test","Direkte Synchronisierung – Test","Synchronisation directe – test","Sincronización directa – prueba","Synchronizacja bezpośrednia – test"};break;
   case "help":a=new String[]{"Vypněte nativní synchronizaci a vyberte Entertainment zónu. U jejích světel nastavte pozici a jas.","Turn off native synchronization and select an Entertainment area. Set the position and brightness of its lights.","Schalten Sie die native Synchronisierung aus und wählen Sie einen Entertainment-Bereich. Stellen Sie Position und Helligkeit seiner Lampen ein.","Désactivez la synchronisation native et choisissez une zone Entertainment. Réglez la position et la luminosité de ses lumières.","Desactive la sincronización nativa y elija una zona Entertainment. Ajuste la posición y el brillo de sus luces.","Wyłącz synchronizację natywną i wybierz strefę Entertainment. Ustaw położenie i jasność jej świateł."};break;
   case "native_off":a=new String[]{"Vypněte nativní synchronizaci Hue a zkuste to znovu.","Turn off native Hue synchronization and try again.","Native Hue-Synchronisierung ausschalten und erneut versuchen.","Désactivez la synchronisation Hue native et réessayez.","Desactive la sincronización Hue nativa e inténtelo de nuevo.","Wyłącz natywną synchronizację Hue i spróbuj ponownie."};break;
   case "ambilight_on":a=new String[]{"Zapněte Ambilight na televizoru a zkuste to znovu.","Turn on TV Ambilight and try again.","TV-Ambilight einschalten und erneut versuchen.","Activez Ambilight sur le téléviseur et réessayez.","Active Ambilight en el televisor e inténtelo de nuevo.","Włącz Ambilight w telewizorze i spróbuj ponownie."};break;
   case "start":a=new String[]{"Spustit přímou synchronizaci","Start direct synchronization","Direkte Synchronisierung starten","Démarrer la synchronisation directe","Iniciar sincronización directa","Uruchom synchronizację bezpośrednią"};break;
   case "stop_hint":a=new String[]{
    "Chování světel po zastavení nastavíte v mobilní aplikaci Philips Hue u vybrané zábavní oblasti.",
    "Set what the lights do after stopping in the Philips Hue mobile app, under the selected Entertainment area.",
    "Das Verhalten der Lampen nach dem Stopp stellen Sie in der mobilen Philips-Hue-App für den gewählten Entertainment-Bereich ein.",
    "Réglez le comportement des lumières à l’arrêt dans la zone de divertissement sélectionnée dans l’application mobile Philips Hue.",
    "Configura qué hacen las luces al detener la sincronización en el área de entretenimiento seleccionada en la aplicación móvil Philips Hue.",
    "Zachowanie świateł po zatrzymaniu ustawisz w aplikacji mobilnej Philips Hue, w wybranym obszarze rozrywki."
   };break;
   case "stop":a=new String[]{"Zastavit synchronizaci","Stop synchronization","Synchronisierung stoppen","Arrêter la synchronisation","Detener la sincronización","Zatrzymaj synchronizację"};break;
   case "load_areas":a=new String[]{"Načíst oblasti","Load areas","Bereiche laden","Charger les zones","Cargar zonas","Wczytaj strefy"};break;
   case "load_lights":a=new String[]{"Načíst žárovky","Load lights","Lampen laden","Charger les lumières","Cargar luces","Wczytaj światła"};break;
   case "lights_settings":a=new String[]{"Nastavení světel","Light settings","Lampeneinstellungen","Réglages des lumières","Configuración de luces","Ustawienia świateł"};break;
   case "save":a=new String[]{"Uložit nastavení","Save settings","Einstellungen speichern","Enregistrer les réglages","Guardar ajustes","Zapisz ustawienia"};break;
   case "smoothing":a=new String[]{"Úprava vyhlazování přechodů barev","Colour transition smoothing","Glättung der Farbübergänge","Lissage des transitions de couleur","Suavizado de transiciones de color","Wygładzanie przejść kolorów"};break;
   case "smoothing_restart":a=new String[]{
    "Uložte nastavení a znovu spusťte přímou synchronizaci.",
    "Save settings and restart Direct Sync.",
    "Einstellungen speichern und die direkte Synchronisierung neu starten.",
    "Enregistrez les réglages et relancez la synchronisation directe.",
    "Guarda los ajustes y reinicia la sincronización directa.",
    "Zapisz ustawienia i uruchom ponownie synchronizację bezpośrednią."
   };break;
   case "smoothing_fast":a=new String[]{"rychlé","fast","schnell","rapide","rápido","szybko"};break;
   case "smoothing_default":a=new String[]{"výchozí","default","Standard","défaut","predeterminado","domyślnie"};break;
   case "smoothing_smooth":a=new String[]{"hladké","smooth","sanft","fluide","suave","płynnie"};break;
   case "saved":a=new String[]{"Nastavení uloženo.","Settings saved.","Einstellungen gespeichert.","Réglages enregistrés.","Ajustes guardados.","Ustawienia zapisano."};break;
   case "busy":a=new String[]{"Nejprve zastavte přímou synchronizaci a dokončete obnovu.","Stop direct synchronization and complete recovery first.","Zuerst die direkte Synchronisierung stoppen und die Wiederherstellung abschließen.","Arrêtez la synchronisation directe et terminez la restauration.","Detenga la sincronización directa y complete la restauración.","Najpierw zatrzymaj synchronizację bezpośrednią i zakończ przywracanie."};break;
   case "area":a=new String[]{"Vyberte oblast.","Select an area.","Bereich auswählen.","Choisissez une zone.","Seleccione una zona.","Wybierz strefę."};break;
   case "lights":a=new String[]{"Načtěte zónu s 1–10 podporovanými světly.","Load an area with 1–10 supported lights.","Laden Sie einen Bereich mit 1–10 unterstützten Lampen.","Chargez une zone avec 1 à 10 lumières compatibles.","Cargue una zona con 1 a 10 luces compatibles.","Wczytaj strefę z 1–10 obsługiwanymi światłami."};break;
   case "loading_failed":a=new String[]{"Načtení selhalo.","Loading failed.","Laden fehlgeschlagen.","Échec du chargement.","Error al cargar.","Wczytywanie nie powiodło się."};break;
   case "recovery":a=new String[]{"Je nutné obnovit světla.","Light recovery is required.","Die Lampen müssen wiederhergestellt werden.","La restauration des lumières est nécessaire.","Es necesario restaurar las luces.","Wymagane jest przywrócenie świateł."};break;
   case "running":a=new String[]{"Přímá synchronizace běží.","Direct synchronization is running.","Direkte Synchronisierung läuft.","La synchronisation directe est active.","La sincronización directa está activa.","Synchronizacja bezpośrednia działa."};break;
   case "recovery_title":a=new String[]{"Obnovení světel","Restore lights","Lampen wiederherstellen","Restaurer les lumières","Restaurar luces","Przywracanie świateł"};break;
   case "recovery_help":a=new String[]{"Předchozí synchronizace skončila před obnovou. Připojte původní můstek a obnovte stav světel.","The previous synchronization ended before recovery. Connect the original Bridge and restore the lights.","Die vorherige Synchronisierung endete vor der Wiederherstellung. Verbinden Sie die ursprüngliche Bridge und stellen Sie die Lampen wieder her.","La synchronisation précédente s'est arrêtée avant la restauration. Connectez le pont d'origine et restaurez les lumières.","La sincronización anterior terminó antes de la restauración. Conecte el puente original y restaure las luces.","Poprzednia synchronizacja zakończyła się przed przywróceniem. Podłącz pierwotny mostek i przywróć stan świateł."};break;
   case "recover":a=new String[]{"Obnovit předchozí stav","Restore previous state","Vorherigen Zustand wiederherstellen","Restaurer l'état précédent","Restaurar estado anterior","Przywróć poprzedni stan"};break;
   case "recover_failed":a=new String[]{"Obnova se nezdařila. Záznam zůstává uložený; zkontrolujte původní můstek a zkuste to znovu.","Recovery failed. The record remains saved; check the original Bridge and retry.","Wiederherstellung fehlgeschlagen. Der Datensatz bleibt gespeichert; prüfen Sie die ursprüngliche Bridge und versuchen Sie es erneut.","Échec de la restauration. Les données sont conservées ; vérifiez le pont d'origine et réessayez.","Error de restauración. Los datos se conservan; compruebe el puente original e inténtelo de nuevo.","Przywracanie nie powiodło się. Zapis pozostał zachowany; sprawdź pierwotny mostek i spróbuj ponownie."};break;
   case "starting":a=new String[]{"Spouštím synchronizaci…","Starting synchronization…","Synchronisierung wird gestartet…","Démarrage de la synchronisation…","Iniciando sincronización…","Uruchamianie synchronizacji…"};break;
   case "stopping":a=new String[]{"Zastavuji synchronizaci…","Stopping synchronization…","Synchronisierung wird gestoppt…","Arrêt de la synchronisation…","Deteniendo la sincronización…","Zatrzymywanie synchronizacji…"};break;
   case "option":a=new String[]{"Přímá Hue synchronizace (experimentální)","Direct Hue synchronization (experimental)","Direkte Hue-Synchronisierung (experimentell)","Synchronisation Hue directe (expérimentale)","Sincronización Hue directa (experimental)","Bezpośrednia synchronizacja Hue (eksperymentalna)"};break;
   case "bridge_select":a=new String[]{"Vybraný spárovaný můstek","Selected paired Bridge","Ausgewählte gekoppelte Bridge","Pont associé sélectionné","Puente vinculado seleccionado","Wybrany sparowany mostek"};break;
   case "blend_zones":a=new String[]{"Míchat okolní zóny (experimentální)","Blend nearby zones (experimental)","Benachbarte Zonen mischen (experimentell)","Mélanger les zones proches (expérimental)","Mezclar zonas cercanas (experimental)","Mieszaj sąsiednie strefy (eksperymentalne)"};break;
   case "compensate_borders":a=new String[]{"Přizpůsobení černým pruhům (experimentální)","Adjust for black bars (experimental)","An schwarze Balken anpassen (experimentell)","Adapter aux bandes noires (expérimental)","Ajustar a franjas negras (experimental)","Dostosuj do czarnych pasów (eksperymentalne)"};break;
   case "border_hint":a=new String[]{"Odhad může ovlivnit také záměrně tmavé okraje obrazu.","The estimate may also affect intentionally dark picture edges.","Die Schätzung kann auch absichtlich dunkle Bildränder beeinflussen.","L’estimation peut également modifier les bords volontairement sombres.","La estimación también puede afectar a bordes intencionadamente oscuros.","Oszacowanie może wpływać także na celowo ciemne brzegi obrazu."};break;
   case "color_correction":a=new String[]{"Barevná korekce (experimentální)","Color correction (experimental)","Farbkorrektur (experimentell)","Correction des couleurs (expérimentale)","Corrección de color (experimental)","Korekcja kolorów (eksperymentalna)"};break;
   case "color_off":a=new String[]{"Vypnuto","Off","Aus","Désactivée","Desactivada","Wyłączona"};break;
   case "color_normal":a=new String[]{"Standardní","Standard","Standard","Standard","Estándar","Standardowa"};break;
   case "color_strong":a=new String[]{"Výraznější","Stronger","Stärker","Plus forte","Más intensa","Silniejsza"};break;
   case "tuning_hint":a=new String[]{"Změny se projeví po dalším spuštění přímé synchronizace.","Changes apply the next time direct synchronization starts.","Änderungen gelten beim nächsten Start der direkten Synchronisierung.","Les modifications s’appliquent au prochain démarrage de la synchronisation directe.","Los cambios se aplican al iniciar de nuevo la sincronización directa.","Zmiany zostaną zastosowane przy następnym uruchomieniu synchronizacji bezpośredniej."};break;
   case "bridge_hint":a=new String[]{"Další můstek nejprve spárujte v nabídce Nastavit Bridge a žárovky. Poté jej zde vyberte; nativního průvodce dokončovat nemusíte.","First pair another Bridge in Set up Bridge and bulbs. Then select it here; completing the native wizard is not necessary.","Eine weitere Bridge zuerst unter Bridge und Lampen einrichten koppeln. Danach hier auswählen; der native Assistent muss nicht abgeschlossen werden.","Associez d’abord un autre pont dans Configurer le pont et les lampes. Sélectionnez-le ensuite ici ; l’assistant natif n’est pas nécessaire.","Primero vincula otro puente en Configurar puente y luces. Después selecciónalo aquí; no es necesario completar el asistente nativo.","Najpierw sparuj kolejny mostek w menu Skonfiguruj mostek i żarówki. Następnie wybierz go tutaj; ukończenie kreatora natywnego nie jest konieczne."};break;
   case "bridge_checking":a=new String[]{"Ověřuji vybraný můstek…","Verifying the selected Bridge…","Ausgewählte Bridge wird geprüft…","Vérification du pont sélectionné…","Verificando el puente seleccionado…","Sprawdzanie wybranego mostka…"};break;
   case "bridge_failed":a=new String[]{"Můstek nebo jeho uložené oprávnění nelze ověřit. Zkontrolujte připojení a párování.","Cannot verify the Bridge or its saved authorization. Check the connection and pairing.","Bridge oder gespeicherte Berechtigung kann nicht geprüft werden. Verbindung und Kopplung prüfen.","Impossible de vérifier le pont ou son autorisation enregistrée. Vérifiez la connexion et l'association.","No se puede verificar el puente o su autorización guardada. Compruebe la conexión y vinculación.","Nie można sprawdzić mostka ani zapisanego uprawnienia. Sprawdź połączenie i parowanie."};break;
   case "bridge_selected":a=new String[]{"Můstek ověřen; načítám jeho nastavení.","Bridge verified; loading its settings.","Bridge geprüft; Einstellungen werden geladen.","Pont vérifié ; chargement des réglages.","Puente verificado; cargando sus ajustes.","Mostek sprawdzony; wczytywanie jego ustawień."};break;
   default:return key;
  }
  return a[i];
 }
}
