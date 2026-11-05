-- =====================================================================================================
-- NightWatch Protocol - Script 0013: preguntas de batalla y retos de ulti en inglés (decisión D15 del módulo 5)
--
-- APLICADA el 2026-11-05 (no se edita más; cualquier corrección va en un script nuevo, 0014 o posterior).
-- NO se ejecuta desde la app ni desde el repositorio: el equipo la pegó a mano en el SQL Editor, DESPUÉS de 0001 a 0012 y de
-- una sola vez. Se puede ejecutar más de una vez (es idempotente).
--
-- QUÉ HACE
--   1. Crea battle_question_translations (texto de cada pregunta por idioma) y ulti_challenge_translations (título
--      de cada reto de ulti por idioma). La respuesta correcta (battle_questions.correct) y el comando esperado
--      (ulti_challenges.expected_command) NO se duplican ni se traducen: viven solo en las tablas originales.
--      Mismas opciones en el mismo orden: A es A en los dos idiomas.
--   2. Inserta las 72 preguntas y los 12 retos en inglés (idioma 'en'). Cada traducción se une a su fila original por
--      el TEXTO en español (pregunta) o por (kind, topic) (reto): no depende del número de id.
--   3. Reemplaza sx_battle_state, battle_sync y battle_play para que acepten un parámetro p_lang con valor por
--      defecto 'es'. Con p_lang = 'en' la pregunta y el título del reto salen en inglés; si falta la traducción o el idioma
--      no existe, salen en español (nunca falla). Sin p_lang, el comportamiento es EXACTAMENTE el de 0008/0011.
--
-- REGLA DEL IDIOMA (decisión propuesta en la etapa 10; el grupo la aprueba al aplicar este script)
--   El idioma es el de QUIEN MIRA, no uno por batalla: cada persona recibe el texto en el idioma que su app envía.
--   Por qué es equivalente a "el mismo idioma para los dos" y mejor:
--     * El servidor nunca muestra la misma pregunta a los dos: solo ve la pregunta quien tiene el turno (sx_battle_state)
--       y cada persona ve solo el título de SU reto de ulti. Nadie compara textos con el rival.
--     * La respuesta correcta y el comando esperado son uno solo por pregunta/reto (id), así que el resultado, el
--       daño, el turno y la ulti son idénticos sin importar el idioma de cada jugador.
--     * Un idioma "por batalla" obligaría a guardar una columna nueva en battles, a cambiar sx_battle_create (que llaman
--       varias funciones) y a elegir quién manda cuando uno juega en español y el otro en inglés. Cada quien jugaría en
--       un idioma que no eligió.
--   La elección la sigue haciendo el servidor: recibe solo un código y decide qué texto devolver (con respaldo).
--
-- COMPATIBILIDAD HACIA ATRÁS
--   * Las apps v2.3.0 y anteriores llaman battle_sync(p_battle) y battle_play(p_battle, p_action, p_turn, p_arg): siguen
--     funcionando (p_lang tiene valor por defecto). Para que PostgREST no vea dos funciones con el mismo nombre, las
--     firmas viejas se ELIMINAN (drop) y se crean las nuevas con el parámetro extra con valor por defecto.
--   * La app v2.4.0 envía p_lang solo con inglés y, si el servidor aún no tiene este script, reintenta sin p_lang.
--
-- SEGURIDAD
--   * Las dos tablas nuevas son CERRADAS como battle_questions y ulti_challenges: RLS activo, SIN políticas y SIN GRANT para
--     anon ni authenticated; solo las lee la función SECURITY DEFINER. (Se pidió "solo lectura para authenticated"; se
--     eligió cerradas porque la app nunca las lee directamente y así el banco no se puede listar entero. Si el grupo
--     prefiere lectura, es una política más en un script nuevo.)
--   * Funciones SECURITY DEFINER con search_path vacío; EXECUTE solo para authenticated (las públicas) o para nadie (sx_).
--   * Todo va dentro de begin/commit: si algo falla a medias, no queda nada. Si alguna pregunta u objetivo quedara sin
--     su traducción al inglés, el script se detiene y deshace todo.
--   * Este archivo no contiene claves, URLs ni datos personales.
--
-- REVERSA: supabase/rollback/0013_rollback.sql. VERIFICACIÓN: supabase/verificacion/0013_verificacion.sql.
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 1. Tablas de traducciones (cerradas)
-- -----------------------------------------------------------------------------------------------------

create table if not exists public.battle_question_translations (
  question_id integer not null references public.battle_questions (id) on delete cascade,
  lang        text    not null check (lang ~ '^[a-z]{2}$' and lang <> 'es'),   -- el español es la tabla original
  question    text    not null check (char_length(question) between 1 and 400),
  option_a    text    not null check (char_length(option_a) between 1 and 200),
  option_b    text    not null check (char_length(option_b) between 1 and 200),
  option_c    text    not null check (char_length(option_c) between 1 and 200),
  option_d    text    not null check (char_length(option_d) between 1 and 200),
  primary key (question_id, lang)
);

create table if not exists public.ulti_challenge_translations (
  challenge_id    integer not null references public.ulti_challenges (id) on delete cascade,
  lang            text    not null check (lang ~ '^[a-z]{2}$' and lang <> 'es'),
  objective_title text    not null check (char_length(objective_title) between 1 and 120),
  primary key (challenge_id, lang)
);

alter table public.battle_question_translations enable row level security;
alter table public.ulti_challenge_translations enable row level security;

-- Cerradas: sin políticas y sin permisos de tabla para la app.
revoke all on public.battle_question_translations from public, anon, authenticated;
revoke all on public.ulti_challenge_translations from public, anon, authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 2. Traducciones al inglés de las 72 preguntas (se unen a la original por su texto en español)
--    Columnas: pregunta en español (clave), pregunta, A, B, C, D. La letra correcta NO se toca.
-- -----------------------------------------------------------------------------------------------------

insert into public.battle_question_translations (question_id, lang, question, option_a, option_b, option_c, option_d)
select q.id, 'en', t.question, t.option_a, t.option_b, t.option_c, t.option_d
from (values
-- ===== Contraseñas y Autenticación =====
('¿Cuál de estas contraseñas es más segura?', 'Which of these passwords is the strongest?', '123456', 'Your_Name2024', 'k7#mQ!9zRt$2', 'password'),
('¿Qué vuelve más difícil adivinar una contraseña?', 'What makes a password harder to guess?', 'That it is short and easy to remember', 'That it is long and mixes letters, numbers and symbols', 'That it uses your date of birth', 'That it repeats the same character'),
('¿Qué debes hacer con tus contraseñas?', 'What should you do with your passwords?', 'Share them with trusted friends', 'Leave them written down next to the computer', 'Send them in a message when someone asks for them', 'Not share them with anyone'),
('¿Qué te ayuda a manejar muchas contraseñas distintas de forma segura?', 'What helps you manage many different passwords securely?', 'A password manager', 'Using the same one on all your accounts', 'Writing them on paper stuck to the monitor', 'Saving them in a chat with a friend'),
('¿Por qué es mala idea reutilizar la misma contraseña en varios sitios?', 'Why is it a bad idea to reuse the same password on several sites?', 'Because sites reject it', 'Because if it leaks on one site, it can be tried on the others', 'Because it becomes slower to type', 'Because the browser deletes it'),
('¿Qué es la autenticación en dos pasos (2FA)?', 'What is two-factor authentication (2FA)?', 'Typing the password twice', 'Having two identical passwords', 'Asking for a second factor, such as a code, in addition to the password', 'Changing the password every two days'),
('Un programa prueba una lista de contraseñas comunes contra una cuenta, una tras otra. ¿Cómo se llama el ataque?', 'A program tries a list of common passwords against an account, one after another. What is this attack called?', 'Dictionary attack', 'Identity spoofing', 'Port scanning', 'Asymmetric encryption'),
('¿Cuál de estas es una mejor contraseña?', 'Which of these is a better password?', 'Password123', 'qwerty', 'FirstnameLastname', 'my-cat-eats-blue-bread-on-the-moon'),
('¿Cómo debería guardar un sitio bien diseñado las contraseñas de sus usuarios?', 'How should a well-designed site store the passwords of its users?', 'In plain text, so they can be recovered', 'Encoded in Base64', 'Encrypted with a key that all staff know', 'As a salted hash, computed with a slow algorithm'),
('¿Qué ventaja tiene una llave de seguridad física (FIDO2) frente a un código por SMS?', 'What advantage does a physical security key (FIDO2) have over an SMS code?', 'It resists phishing, because it only responds to the legitimate site', 'It is cheaper', 'It needs no device at all', 'It allows shorter passwords'),
('Un sitio te avisa de que tu contraseña apareció en una filtración. ¿Qué haces primero?', 'A site warns you that your password appeared in a breach. What do you do first?', 'Wait and see if anything happens', 'Change just one letter', 'Change it there and on any other site where you used it', 'Delete your email'),
('¿Qué es el "password spraying"?', 'What is "password spraying"?', 'Trying millions of passwords against a single account', 'Trying a few common passwords against many accounts to avoid lockouts', 'Stealing passwords with a fake keyboard', 'Guessing passwords with social engineering'),
-- ===== Phishing =====
('¿Qué es el phishing?', 'What is phishing?', 'A virus that deletes files', 'A deception to make you hand over data or money by posing as someone you trust', 'A type of password', 'A program to clean up your email'),
('Un correo de "tu banco" dice que tu cuenta se bloqueará en una hora si no entras a un enlace. ¿Qué señal de alerta tiene?', 'An email from "your bank" says your account will be blocked in one hour unless you follow a link. What warning sign does it show?', 'It creates urgency so you act without thinking', 'It is written in English', 'It mentions a bank', 'It carries the bank logo'),
('¿Qué haces antes de tocar un enlace sospechoso?', 'What do you do before tapping a suspicious link?', 'Tap it to see where it goes', 'Forward it to your contacts', 'Open it with another account', 'Check the real address of the link (for example, by pressing and holding it)'),
('¿Qué haces con un correo que parece phishing?', 'What do you do with an email that looks like phishing?', 'Reply asking for explanations', 'Forward it as a warning', 'Not open links or attachments, and report or delete it', 'Download the attachment on another device'),
('¿Cuál de estas direcciones parece una copia falsa de banco-ejemplo.com?', 'Which of these addresses looks like a fake copy of banco-ejemplo.com?', 'app.banco-ejemplo.com', 'www.banco-ejemplo.com', 'banco-ejemp1o.com', 'banco-ejemplo.com'),
('¿Qué es el "spear phishing"?', 'What is "spear phishing"?', 'Mass phishing with no specific target', 'Phishing aimed at a specific person, using their data to look real', 'Phishing by phone call', 'An antispam filter'),
('¿Qué es el "smishing"?', 'What is "smishing"?', 'Phishing by text message (SMS)', 'Phishing by voice call', 'Phishing by email', 'Phishing by QR codes'),
('Un adjunto se llama "factura.pdf.exe". ¿Qué indica?', 'An attachment is named "factura.pdf.exe". What does it indicate?', 'It is a normal PDF', 'It is a password-protected PDF', 'It is a compressed file', 'It is an executable program disguised as a PDF'),
('¿Qué es el fraude BEC (Business Email Compromise)?', 'What is BEC (Business Email Compromise) fraud?', 'Someone poses as an executive or supplier to ask for payments or data', 'A virus that spreads by email', 'An encryption for emails', 'An email server'),
('¿Qué mecanismos ayudan a comprobar que un correo viene realmente del dominio que dice?', 'Which mechanisms help check that an email really comes from the domain it claims?', 'HTTP, FTP and SMTP', 'WPA2, WEP and WPS', 'RAID, NAS and SAN', 'SPF, DKIM and DMARC'),
('Una página falsa muestra el candado de HTTPS. ¿Qué significa eso?', 'A fake page shows the HTTPS padlock. What does that mean?', 'That the site is legitimate', 'That the connection is encrypted, but it does not prove the site is who it claims to be', 'That the site has no viruses', 'That the government approved it'),
('Un gestor de contraseñas no rellena tu contraseña en un sitio que parece el de siempre. ¿Por qué eso ayuda contra el phishing?', 'A password manager does not fill in your password on a site that looks like the usual one. Why does that help against phishing?', 'Because it encrypts the site', 'Because it blocks fake emails', 'Because it only autofills on the saved domain, so the lack of autofill is a warning', 'Because it changes the password by itself'),
-- ===== Malware =====
('¿Qué es el malware?', 'What is malware?', 'Software made to damage, spy on or take control of a device', 'A type of hardware', 'A Wi-Fi network', 'A browser'),
('¿Qué hace el ransomware?', 'What does ransomware do?', 'Speeds up the device', 'Encrypts your files and demands a payment to return them', 'Improves the antivirus', 'Deletes the browser history'),
('¿Cuál es una buena forma de reducir el riesgo de malware?', 'What is a good way to reduce the risk of malware?', 'Open all attachments', 'Download "cracked" programs', 'Install apps only from official stores and keep everything up to date', 'Turn off the antivirus to go faster'),
('¿Cuál de estos es un ejemplo de malware?', 'Which of these is an example of malware?', 'A word processor', 'A printer driver', 'An official screensaver', 'A spy program (spyware)'),
('¿Qué es un troyano?', 'What is a Trojan?', 'A virus that copies itself across the network', 'A type of antivirus', 'A type of firewall', 'A program that pretends to be legitimate to sneak in something harmful'),
('¿Qué distingue a un gusano (worm)?', 'What sets a worm apart?', 'It spreads by itself across the network, without anyone running a file', 'It only affects Mac computers', 'It only deletes images', 'It always needs a USB drive'),
('¿Qué hace un keylogger?', 'What does a keylogger do?', 'Speeds up the keyboard', 'Records what you type', 'Changes the keyboard language', 'Encrypts the disk'),
('Sospechas que tu equipo tiene malware. ¿Qué haces primero?', 'You suspect your device has malware. What do you do first?', 'Keep using it as usual', 'Pay whoever asks you to', 'Disconnect it from the network and scan it with an up-to-date antivirus', 'Restart and wait'),
('¿Qué es una botnet?', 'What is a botnet?', 'A public Wi-Fi network', 'A type of VPN', 'A network of infected devices that an attacker controls remotely', 'A password database'),
('¿Qué es un rootkit?', 'What is a rootkit?', 'A legitimate toolkit for administrators', 'A type of phishing', 'A file encryptor', 'Malware that hides its presence and keeps privileged access'),
('¿Por qué ayuda contra el ransomware una copia de seguridad desconectada de la red (offline)?', 'Why does a backup disconnected from the network (offline) help against ransomware?', 'Because ransomware cannot encrypt it', 'Because it is faster', 'Because it is in the same folder', 'Because it prevents infection'),
('¿Para qué sirve analizar un archivo sospechoso en una "sandbox"?', 'What is the purpose of analyzing a suspicious file in a "sandbox"?', 'To delete it', 'To run it in an isolated environment and observe its behavior without risk to the device', 'To compress it', 'To send it by email'),
-- ===== Ingeniería Social =====
('¿Qué es la ingeniería social?', 'What is social engineering?', 'Programming social networks', 'Manipulating people to obtain information or access', 'A networking course', 'A type of encryption'),
('¿Qué dato no te pediría nunca un soporte técnico legítimo?', 'What information would a legitimate tech support never ask you for?', 'The ticket number', 'The device name', 'A description of the problem', 'Your password'),
('¿Qué es el "pretexting"?', 'What is "pretexting"?', 'Making up a believable story to gain your trust and get data out of you', 'A type of malware', 'A network protocol', 'A backup'),
('¿Qué es el "tailgating"?', 'What is "tailgating"?', 'A virus', 'An encryption', 'Entering a restricted area by closely following someone authorized', 'A VPN'),
('Alguien deja memorias USB "perdidas" en un estacionamiento para que las conecten. ¿Qué técnica es?', 'Someone leaves "lost" USB drives in a parking lot for people to plug in. What technique is this?', 'Baiting', 'Smishing', 'Spoofing', 'Doxing'),
('Un mensaje dice: "Solo te quedan 5 minutos para reclamar tu premio". ¿Qué principio de manipulación usa?', 'A message says: "You only have 5 minutes left to claim your prize". What manipulation principle does it use?', 'Authority', 'Reciprocity', 'Urgency', 'Liking'),
('Un "jefe" te escribe desde un correo nuevo y te pide comprar tarjetas de regalo con urgencia. ¿Qué haces?', 'A "boss" writes to you from a new email address and urgently asks you to buy gift cards. What do you do?', 'Buy them quickly', 'Verify the request through another channel you already know', 'Reply with your details', 'Forward it to a coworker'),
('¿Qué es el "shoulder surfing"?', 'What is "shoulder surfing"?', 'A water sport', 'Malware', 'A type of network', 'Looking over someone''s shoulder to see passwords or screens'),
('¿Qué es el "vishing"?', 'What is "vishing"?', 'Phishing by SMS', 'Phishing by voice call', 'Phishing by QR', 'Phishing by email'),
('¿Qué es el "quishing"?', 'What is "quishing"?', 'Phishing with malicious QR codes', 'Phishing by voice', 'Ransomware', 'An encryption'),
('¿Qué información pública de tus redes sociales podría ayudar a un atacante a adivinar tus preguntas de seguridad?', 'What public information on your social media could help an attacker guess your security questions?', 'Your phone''s language', 'The time you post', 'Your phone model', 'Your pet''s name, hometown or school'),
('¿Cuál es la mejor defensa general contra la ingeniería social?', 'What is the best general defense against social engineering?', 'A more powerful antivirus', 'A longer password', 'Distrusting urgent or unusual requests and confirming them through another channel', 'A more expensive firewall'),
-- ===== Redes Seguras =====
('¿Qué es más seguro en tu wifi doméstica?', 'What is safer on your home Wi-Fi?', 'Leaving it without a password', 'Using WEP', 'Putting your personal details in the network name', 'WPA3 (or WPA2) with a strong password'),
('¿Qué riesgo tiene una wifi pública abierta?', 'What risk does an open public Wi-Fi have?', 'It uses more battery', 'It is slower', 'Someone can spy on traffic that is not encrypted', 'None'),
('¿Qué hace una VPN?', 'What does a VPN do?', 'Speeds up the internet', 'Creates an encrypted tunnel for your traffic', 'Removes viruses', 'Replaces the antivirus'),
('¿Qué deberías cambiar en un router nuevo?', 'What should you change on a new router?', 'The default administration password', 'The color', 'The antenna', 'Nothing: it comes secure'),
('¿Qué indica "https://" al inicio de una dirección?', 'What does "https://" at the start of an address indicate?', 'That the site belongs to the government', 'That the connection to the site is encrypted', 'That the site has no ads', 'That the site is official'),
('¿Qué es un ataque "man-in-the-middle"?', 'What is a "man-in-the-middle" attack?', 'An attack that wipes disks', 'A type of VPN', 'A backup', 'An attacker who gets between two parties and can read or alter what they send'),
('¿Para qué sirve tener una red de invitados separada en tu router?', 'What is a separate guest network on your router for?', 'So a compromised guest device cannot reach your devices', 'To go faster', 'To use fewer cables', 'To hide the network name'),
('¿Qué es WPS en un router y qué conviene hacer con él?', 'What is WPS on a router and what should you do with it?', 'A very strong encryption; leave it on', 'A router antivirus; keep it updated', 'A PIN-based pairing with known weaknesses; turn it off', 'A built-in VPN; always turn it on'),
('¿Qué es el "DNS spoofing"?', 'What is "DNS spoofing"?', 'A port scan', 'A disk encryption', 'Forging DNS responses to take you to a fake site', 'A backup'),
('¿Qué hace un "evil twin"?', 'What does an "evil twin" do?', 'Duplicates your files', 'Imitates a legitimate Wi-Fi network to capture the traffic of whoever connects', 'It is a type of switch', 'It is a free VPN'),
('¿Qué protocolo reemplaza a Telnet con comunicación cifrada para administrar equipos a distancia?', 'Which protocol replaces Telnet with encrypted communication to manage devices remotely?', 'FTP', 'HTTP', 'SMTP', 'SSH'),
('¿Para qué sirve un escaneo de puertos con una herramienta como nmap en tu propia red?', 'What is a port scan with a tool like nmap on your own network for?', 'To discover which services are exposed', 'To increase speed', 'To encrypt traffic', 'To delete logs'),
-- ===== Firewall =====
('¿Dónde puede estar un firewall?', 'Where can a firewall be?', 'Only in a router', 'Only in the cloud', 'As a program on a device or as a device on the network', 'Only in the browser'),
('¿Qué significa "denegar por defecto"?', 'What does "deny by default" mean?', 'Blocking everything that is not explicitly allowed', 'Allowing everything except what is forbidden', 'Deleting all the rules', 'Turning off the firewall'),
('¿Qué es un puerto de red?', 'What is a network port?', 'A cable', 'A number that identifies a service on a device (for example, 443 for HTTPS)', 'A type of virus', 'A password'),
('Un programa desconocido te pide que desactives el firewall para instalarse. ¿Qué haces?', 'An unknown program asks you to turn off the firewall to install itself. What do you do?', 'Turn it off', 'Turn it off just for a while', 'Restart the device', 'Not do it and check where the program comes from'),
('¿Qué comando de ufw bloquea el tráfico que viene de la IP 198.51.100.9?', 'Which ufw command blocks traffic coming from IP 198.51.100.9?', 'ufw deny from 198.51.100.9', 'ufw allow from 198.51.100.9', 'ufw status', 'ufw reset'),
('¿Qué puerto usa HTTPS por defecto?', 'Which port does HTTPS use by default?', '21', '443', '25', '80'),
('¿Qué diferencia a un firewall "con estado" (stateful)?', 'What sets a "stateful" firewall apart?', 'It only looks at the destination address', 'It only works with Wi-Fi', 'It blocks all traffic', 'It tracks the state of connections, not just each loose packet'),
('¿Qué hace la regla "ufw allow 22/tcp"?', 'What does the rule "ufw allow 22/tcp" do?', 'Blocks port 22', 'Deletes the rule for port 22', 'Allows incoming connections to port 22 (SSH)', 'Changes the SSH port'),
('¿Qué es una DMZ en una red?', 'What is a DMZ in a network?', 'A backup', 'A guest Wi-Fi network without encryption', 'A network antivirus', 'A separate zone where services exposed to the Internet live, isolated from the internal network'),
('¿Por qué importa el orden de las reglas de un firewall?', 'Why does the order of a firewall''s rules matter?', 'It does not matter', 'They are all applied at once', 'They are evaluated in order and the first one that matches is usually the one applied', 'Only the last one counts'),
('¿Qué diferencia hay entre DROP y REJECT en un firewall?', 'What is the difference between DROP and REJECT in a firewall?', 'DROP silently discards the packet; REJECT tells the source it was rejected', 'DROP tells the source; REJECT does not', 'They are the same', 'DROP opens the port; REJECT closes it'),
('¿Qué hace un WAF (Web Application Firewall)?', 'What does a WAF (Web Application Firewall) do?', 'Encrypts disks', 'Filters HTTP traffic to a web application to stop attacks such as SQL injection or XSS', 'Replaces the antivirus', 'Shares the Wi-Fi')
) as t(es_question, question, option_a, option_b, option_c, option_d)
join public.battle_questions q on q.question = t.es_question
on conflict (question_id, lang) do update
  set question = excluded.question,
      option_a = excluded.option_a,
      option_b = excluded.option_b,
      option_c = excluded.option_c,
      option_d = excluded.option_d;

-- -----------------------------------------------------------------------------------------------------
-- 3. Traducciones al inglés de los 12 retos de ulti (se unen por tipo y tema; el comando NO se traduce)
-- -----------------------------------------------------------------------------------------------------

insert into public.ulti_challenge_translations (challenge_id, lang, objective_title)
select c.id, 'en', t.objective_title
from (values
('attack',  'CONTRASENAS',       'Test a weak password'),
('attack',  'PHISHING',          'Inspect the sender'),
('attack',  'MALWARE',           'Scan the file with the antivirus'),
('attack',  'INGENIERIA_SOCIAL', 'Verify the caller''s identity'),
('attack',  'REDES_SEGURAS',     'Investigate the suspicious IP'),
('attack',  'FIREWALL',          'Block the exposed SSH'),
('defense', 'CONTRASENAS',       'Test a strong password'),
('defense', 'PHISHING',          'Compare with the official domain'),
('defense', 'MALWARE',           'Quarantine the file'),
('defense', 'INGENIERIA_SOCIAL', 'Report the social engineering attempt'),
('defense', 'REDES_SEGURAS',     'View the access log'),
('defense', 'FIREWALL',          'Verify the active rules')
) as t(kind, topic, objective_title)
join public.ulti_challenges c on c.kind = t.kind and c.topic = t.topic
on conflict (challenge_id, lang) do update set objective_title = excluded.objective_title;

-- Si alguna pregunta o reto quedó sin su traducción (el texto en español de 0009 fue editado, por ejemplo), se
-- detiene y se deshace todo, para no dejar una batalla a medias en inglés.
do $comprobacion$
begin
  if exists (
    select 1 from public.battle_questions q
    where not exists (select 1 from public.battle_question_translations t where t.question_id = q.id and t.lang = 'en')
  ) then
    raise exception '0013: hay preguntas de battle_questions sin traducción al inglés (¿cambió el texto en español de 0009?)';
  end if;
  if exists (
    select 1 from public.ulti_challenges c
    where not exists (select 1 from public.ulti_challenge_translations t where t.challenge_id = c.id and t.lang = 'en')
  ) then
    raise exception '0013: hay retos de ulti sin traducción al inglés';
  end if;
end
$comprobacion$;

-- -----------------------------------------------------------------------------------------------------
-- 4. Funciones con p_lang (valor por defecto 'es'). Las firmas viejas se eliminan para que no haya dos funciones con
--    el mismo nombre (PostgREST no sabría cuál elegir cuando falta p_lang). Quien llama con los argumentos de antes
--    cae en la nueva por los valores por defecto.
-- -----------------------------------------------------------------------------------------------------

drop function if exists public.battle_sync(uuid);
drop function if exists public.battle_play(uuid, text, integer, text);
drop function if exists public.sx_battle_state(uuid, uuid);

-- 4.1 sx_battle_state (de 0011, interna). Igual que 0011, salvo el texto de la pregunta y el título del reto de ulti,
-- que salen en p_lang si existe la traducción. 'correct' y el comando esperado no salen nunca.
create or replace function public.sx_battle_state(p_battle uuid, p_user uuid, p_lang text default 'es')
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  v_b     public.battles%rowtype;
  v_now   timestamptz := clock_timestamp();
  v_seat  integer;
  v_rival uuid;
  v_out   jsonb;
  v_q     public.battle_questions%rowtype;
  v_qt    public.battle_question_translations%rowtype;   -- 0013
  v_u     public.battle_ultis%rowtype;
  v_title text;
  v_is_att boolean;
  v_last  public.battle_turns%rowtype;
  v_res   public.battle_results%rowtype;
  v_ulti  public.battle_ultis%rowtype;
  v_msp   public.social_profile%rowtype;  -- 0010: perfil social de quien mira
  v_rsp   public.social_profile%rowtype;  -- 0010: perfil social del rival
  v_lang  text := lower(left(btrim(coalesce(p_lang, 'es')), 10));   -- 0013
begin
  select * into v_b from public.battles where id = p_battle;
  v_seat  := case when v_b.player1_id = p_user then 1 else 2 end;
  v_rival := case when v_seat = 1 then v_b.player2_id else v_b.player1_id end;
  select * into v_msp from public.social_profile where user_id = p_user;   -- 0010
  select * into v_rsp from public.social_profile where user_id = v_rival;  -- 0010

  v_out := jsonb_build_object(
    'battle_id', v_b.id,
    'mode', v_b.mode,
    'status', v_b.status,
    'version', v_b.version,
    'server_now', v_now,
    'my_seat', v_seat,
    'turn_number', v_b.turn_number,
    'me', jsonb_build_object(
      'hp', case when v_seat = 1 then v_b.hp1 else v_b.hp2 end,
      'correct_streak', case when v_seat = 1 then v_b.correct_streak1 else v_b.correct_streak2 end,
      'ulti_charged', case when v_seat = 1 then v_b.ulti_charged1 else v_b.ulti_charged2 end,
      'avatar_key', case when v_msp.avatar_hidden then null else v_msp.avatar_key end,              -- 0010, 0011
      'avatar_version', case when v_msp.avatar_hidden then null else v_msp.avatar_version end       -- 0010, 0011
    ),
    'rival', jsonb_build_object(
      'username', public.sx_username(v_rival),
      'hp', case when v_seat = 1 then v_b.hp2 else v_b.hp1 end,
      'seconds_since_seen', round(extract(epoch from (v_now - case when v_seat = 1 then v_b.last_seen2 else v_b.last_seen1 end))::numeric, 1),
      'avatar_key', case when v_rsp.avatar_hidden then null else v_rsp.avatar_key end,              -- 0010, 0011
      'avatar_version', case when v_rsp.avatar_hidden then null else v_rsp.avatar_version end       -- 0010, 0011
    ),
    'end_reason', v_b.end_reason
  );

  if v_b.status = 'active' then
    v_out := v_out || jsonb_build_object(
      'turn', jsonb_build_object(
        'is_mine', v_b.turn_seat = v_seat,
        'phase', v_b.turn_phase,
        'seconds_left', greatest(0, round(extract(epoch from (v_b.turn_deadline - v_now))::numeric, 1))
      )
    );

    if v_b.turn_phase = 'question' and v_b.turn_seat = v_seat then
      select * into v_q from public.battle_questions where id = v_b.current_question_id;
      -- 0013: la traducción pedida, si existe; si no, el español de la tabla original.
      if v_lang <> 'es' then
        select * into v_qt from public.battle_question_translations where question_id = v_q.id and lang = v_lang;
      end if;
      if v_lang <> 'es' and found then
        v_out := v_out || jsonb_build_object(
          'question', jsonb_build_object(
            'id', v_q.id,
            'text', v_qt.question,
            'options', jsonb_build_object('A', v_qt.option_a, 'B', v_qt.option_b, 'C', v_qt.option_c, 'D', v_qt.option_d)
          )
        );
      else
        v_out := v_out || jsonb_build_object(
          'question', jsonb_build_object(
            'id', v_q.id,
            'text', v_q.question,
            'options', jsonb_build_object('A', v_q.option_a, 'B', v_q.option_b, 'C', v_q.option_c, 'D', v_q.option_d)
          )
        );
      end if;
    end if;

    if v_b.turn_phase = 'ulti' then
      select * into v_u from public.battle_ultis
       where battle_id = p_battle and resolved_at is null
       order by id desc limit 1;
      if found then
        v_is_att := v_u.attacker_seat = v_seat;
        select coalesce(
                 (select t.objective_title from public.ulti_challenge_translations t where t.challenge_id = c.id and t.lang = v_lang),   -- 0013
                 c.objective_title)
          into v_title
          from public.ulti_challenges c
         where c.id = case when v_is_att then v_u.attack_challenge_id else v_u.defense_challenge_id end;
        v_out := v_out || jsonb_build_object(
          'ulti', jsonb_build_object(
            'role', case when v_is_att then 'attacker' else 'defender' end,
            'objective', v_title,
            'attempts_left', 3 - case when v_is_att then v_u.attacker_attempts else v_u.defender_attempts end,
            'my_result', case when v_is_att then v_u.attacker_result else v_u.defender_result end
          )
        );
      end if;
    end if;
  end if;

  select * into v_last from public.battle_turns where battle_id = p_battle order by turn_number desc limit 1;
  if found then
    v_out := v_out || jsonb_build_object(
      'last_turn', jsonb_build_object(
        'number', v_last.turn_number,
        'by_me', v_last.seat = v_seat,
        'outcome', v_last.outcome,
        'damage', v_last.damage
      )
    );
    if v_last.outcome = 'ulti' then
      select * into v_ulti from public.battle_ultis
       where battle_id = p_battle and turn_number = v_last.turn_number;
      if found then
        v_out := v_out || jsonb_build_object(
          'last_ulti', jsonb_build_object(
            'shield', v_ulti.shield,
            'attacker_result', v_ulti.attacker_result,
            'defender_result', v_ulti.defender_result
          )
        );
      end if;
    end if;
  end if;

  if v_b.status <> 'active' then
    select * into v_res from public.battle_results where battle_id = p_battle and user_id = p_user;
    if found then
      v_out := v_out || jsonb_build_object(
        'result', jsonb_build_object(
          'outcome', v_res.result,
          'xp', v_res.xp_awarded,
          'hp_left', v_res.hp_left,
          'xp_total', public.sx_xp(p_user),
          'level', public.sx_user_level(p_user),
          'weak_topics', coalesce((
            select jsonb_agg(w.topic order by w.misses desc, w.topic)
            from (
              select q.topic, count(*) as misses
              from public.battle_turns t
              join public.battle_questions q on q.id = t.question_id
              where t.battle_id = p_battle and t.seat = v_seat and t.outcome in ('incorrect', 'timeout')
              group by q.topic
              order by count(*) desc, q.topic
              limit 2
            ) w
          ), '[]'::jsonb)
        )
      );
    end if;
  end if;

  return v_out;
end;
$$;

-- 4.2 battle_sync (de 0008): igual, con p_lang.
create or replace function public.battle_sync(p_battle uuid, p_lang text default 'es')
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := (select auth.uid());
  v_b  public.battles%rowtype;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;

  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_me not in (v_b.player1_id, v_b.player2_id) then
    raise exception 'not_found';
  end if;

  if v_b.status = 'active' then
    update public.battles
       set last_seen1 = case when player1_id = v_me then clock_timestamp() else last_seen1 end,
           last_seen2 = case when player2_id = v_me then clock_timestamp() else last_seen2 end
     where id = p_battle;
    perform public.sx_battle_resolve(p_battle);
  end if;

  return public.sx_battle_state(p_battle, v_me, p_lang);
end;
$$;

-- 4.3 battle_play (de 0008): igual, con p_lang (solo cambia el texto que devuelve; la lógica del juego no se toca).
create or replace function public.battle_play(p_battle uuid, p_action text, p_turn integer default null, p_arg text default null, p_lang text default 'es')
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me     uuid := (select auth.uid());
  v_b      public.battles%rowtype;
  v_seat   integer;
  v_q      public.battle_questions%rowtype;
  v_correct boolean;
  v_damage integer;
  v_u      public.battle_ultis%rowtype;
  v_ch     public.ulti_challenges%rowtype;
  v_att    integer;
  v_def    integer;
  v_t      timestamptz;
  v_is_att boolean;
begin
  if v_me is null then
    raise exception 'no_session';
  end if;
  if p_action is null or p_action not in ('answer', 'use_ulti', 'ulti_submit', 'abandon') then
    raise exception 'bad_action';
  end if;

  select * into v_b from public.battles where id = p_battle for update;
  if not found or v_me not in (v_b.player1_id, v_b.player2_id) then
    raise exception 'not_found';
  end if;
  if v_b.status <> 'active' then
    raise exception 'battle_over';
  end if;
  v_seat := case when v_b.player1_id = v_me then 1 else 2 end;

  update public.battles
     set last_seen1 = case when v_seat = 1 then clock_timestamp() else last_seen1 end,
         last_seen2 = case when v_seat = 2 then clock_timestamp() else last_seen2 end
   where id = p_battle;
  perform public.sx_battle_resolve(p_battle);

  select * into v_b from public.battles where id = p_battle;
  if v_b.status <> 'active' then
    return public.sx_battle_state(p_battle, v_me, p_lang);
  end if;

  if p_action = 'abandon' then
    perform public.sx_battle_finish(p_battle, case when v_seat = 1 then 2 else 1 end, 'abandon');

  elsif p_action = 'answer' then
    if v_b.turn_phase <> 'question' or v_b.turn_seat <> v_seat then
      raise exception 'not_your_turn';
    end if;
    if p_turn is distinct from v_b.turn_number then
      raise exception 'stale_turn';
    end if;
    if p_arg is null or upper(p_arg) not in ('A', 'B', 'C', 'D') then
      raise exception 'bad_option';
    end if;

    select * into v_q from public.battle_questions where id = v_b.current_question_id;
    v_correct := v_q.correct = upper(p_arg);
    v_damage  := case when v_correct then 20 else 0 end;

    insert into public.battle_turns (battle_id, turn_number, seat, question_id, outcome, chosen_option, damage)
    values (p_battle, v_b.turn_number, v_seat, v_q.id, case when v_correct then 'correct' else 'incorrect' end, upper(p_arg), v_damage);

    -- Racha de aciertos de la ulti (D1): 3 seguidos la cargan. Un fallo reinicia el contador, pero una ulti ya
    -- cargada SIGUE cargada (D1: responder una pregunta normal no la gasta).
    update public.battles
       set hp1             = case when v_seat = 2 then greatest(0, hp1 - v_damage) else hp1 end,
           hp2             = case when v_seat = 1 then greatest(0, hp2 - v_damage) else hp2 end,
           correct_streak1 = case when v_seat = 1 then (case when v_correct then least(3, correct_streak1 + 1) else 0 end) else correct_streak1 end,
           correct_streak2 = case when v_seat = 2 then (case when v_correct then least(3, correct_streak2 + 1) else 0 end) else correct_streak2 end,
           ulti_charged1   = case when v_seat = 1 and v_correct and correct_streak1 + 1 >= 3 then true else ulti_charged1 end,
           ulti_charged2   = case when v_seat = 2 and v_correct and correct_streak2 + 1 >= 3 then true else ulti_charged2 end,
           version         = version + 1
     where id = p_battle;

    select * into v_b from public.battles where id = p_battle;
    if (v_seat = 1 and v_b.hp2 = 0) or (v_seat = 2 and v_b.hp1 = 0) then
      perform public.sx_battle_finish(p_battle, v_seat, 'hp_zero');
    else
      perform public.sx_advance_turn(p_battle);
    end if;

  elsif p_action = 'use_ulti' then
    if v_b.turn_phase <> 'question' or v_b.turn_seat <> v_seat then
      raise exception 'not_your_turn';
    end if;
    if not (case when v_seat = 1 then v_b.ulti_charged1 else v_b.ulti_charged2 end) then
      raise exception 'ulti_not_charged';
    end if;

    select c.id into v_att from public.ulti_challenges c where c.kind = 'attack' and c.active order by random() limit 1;
    select c.id into v_def from public.ulti_challenges c where c.kind = 'defense' and c.active order by random() limit 1;
    if v_att is null or v_def is null then
      raise exception 'bank_empty';
    end if;

    v_t := clock_timestamp();
    insert into public.battle_ultis (battle_id, turn_number, attacker_seat, attack_challenge_id, defense_challenge_id, started_at, deadline)
    values (p_battle, v_b.turn_number, v_seat, v_att, v_def, v_t, v_t + interval '2 minutes');

    update public.battles
       set turn_phase = 'ulti', turn_deadline = v_t + interval '2 minutes', current_question_id = null, version = version + 1
     where id = p_battle;

  elsif p_action = 'ulti_submit' then
    if v_b.turn_phase <> 'ulti' then
      raise exception 'no_ulti';
    end if;
    select * into v_u from public.battle_ultis
     where battle_id = p_battle and resolved_at is null
     order by id desc limit 1 for update;
    if not found then
      raise exception 'no_ulti';
    end if;
    if p_arg is null or char_length(p_arg) > 200 then
      raise exception 'bad_command';
    end if;

    -- La hora del acierto se toma DESPUÉS de tener el candado de la batalla: así el orden de los aciertos
    -- (quién llegó primero, para el escudo) es el orden real de llegada al servidor.
    v_t := clock_timestamp();
    v_is_att := v_u.attacker_seat = v_seat;

    if v_is_att then
      if v_u.attacker_result <> 'pending' then
        raise exception 'already_resolved';
      end if;
      select * into v_ch from public.ulti_challenges where id = v_u.attack_challenge_id;
      if public.sx_normalize_command(p_arg) = public.sx_normalize_command(v_ch.expected_command) then
        update public.battle_ultis
           set attacker_attempts = attacker_attempts + 1, attacker_result = 'solved', attacker_at = v_t
         where id = v_u.id;
        perform public.sx_ulti_resolve(p_battle);
      elsif v_u.attacker_attempts + 1 >= 3 then
        update public.battle_ultis
           set attacker_attempts = attacker_attempts + 1, attacker_result = 'failed', attacker_at = v_t
         where id = v_u.id;
        perform public.sx_ulti_resolve(p_battle);
      else
        update public.battle_ultis set attacker_attempts = attacker_attempts + 1 where id = v_u.id;
      end if;
    else
      if v_u.defender_result <> 'pending' then
        raise exception 'already_resolved';
      end if;
      select * into v_ch from public.ulti_challenges where id = v_u.defense_challenge_id;
      if public.sx_normalize_command(p_arg) = public.sx_normalize_command(v_ch.expected_command) then
        update public.battle_ultis
           set defender_attempts = defender_attempts + 1, defender_result = 'solved', defender_at = v_t
         where id = v_u.id;
      elsif v_u.defender_attempts + 1 >= 3 then
        update public.battle_ultis
           set defender_attempts = defender_attempts + 1, defender_result = 'failed', defender_at = v_t
         where id = v_u.id;
      else
        update public.battle_ultis set defender_attempts = defender_attempts + 1 where id = v_u.id;
      end if;
    end if;
  end if;

  return public.sx_battle_state(p_battle, v_me, p_lang);
end;
$$;

-- -----------------------------------------------------------------------------------------------------
-- 5. Permisos de EJECUCIÓN (como en 0008: revoke explícito y luego grant solo a authenticated)
-- -----------------------------------------------------------------------------------------------------

revoke all on function public.battle_sync(uuid, text) from public, anon;
grant execute on function public.battle_sync(uuid, text) to authenticated;

revoke all on function public.battle_play(uuid, text, integer, text, text) from public, anon;
grant execute on function public.battle_play(uuid, text, integer, text, text) to authenticated;

revoke all on function public.sx_battle_state(uuid, uuid, text) from public, anon, authenticated;

commit;

-- =====================================================================================================
-- Advertencias esperadas del Security Advisor tras este script (documentar en docs/superpowers/supabase-configuracion.md)
--   * battle_sync(uuid, text) y battle_play(uuid, text, integer, text, text): "Signed-In Users Can Execute SECURITY DEFINER
--     Function", las MISMAS ya aceptadas para las firmas de 0008 (la firma cambió, la razón no).
--   * (Info) "RLS Enabled No Policy": 2 tablas más, battle_question_translations y ulti_challenge_translations. Se aceptan
--     igual que battle_questions y ulti_challenges: están cerradas a propósito.
-- =====================================================================================================
