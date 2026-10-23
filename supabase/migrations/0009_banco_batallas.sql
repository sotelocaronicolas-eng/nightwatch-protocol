-- =====================================================================================================
-- NightWatch Protocol - Script 0009: contenido de las batallas (curva de XP, banco de preguntas y retos de ulti)
--
-- BORRADOR sin aplicar. NO se ejecuta desde la app ni desde el repositorio: el equipo lo revisa y, si lo
-- aprueba, lo pega a mano en el SQL Editor, DESPUÉS de 0001 a 0008 y una sola vez. Mientras tenga esta
-- línea de "BORRADOR" puede editarse; una vez aplicado deja de editarse y cualquier corrección del contenido
-- va en un script nuevo (0010 o posterior) que actualiza las filas.
--
-- Fuente: docs/superpowers/specs/2026-10-21-modulo-4-design.md (secciones 8.2, 9.4 y 9.5) y las decisiones
-- del grupo (D8, D9, O1, O12). Las 72 preguntas son EXACTAMENTE las del borrador de la sección 9.4 de la spec.
--
-- QUÉ INCLUYE (solo datos; las tablas ya existen desde 0008)
--   1. fighter_level_curve: niveles 1 a 20, con min_xp = (nivel - 1) * 100 (O1: 100 XP por nivel, tope 20).
--   2. battle_questions: 72 preguntas (6 temas x 12; 3 escalones x 4 por tema; 4 opciones; una correcta).
--        escalón 1 = niveles de combatiente 1 a 7, escalón 2 = 8 a 14, escalón 3 = 15 a 20 (O12).
--        Letras correctas: 18 de cada una (3 por letra en cada tema).
--   3. ulti_challenges: 12 retos de terminal (D4, O10): 6 de ataque y 6 de defensa, uno de cada tipo por tema.
--        Los comandos son EXACTAMENTE los de los laboratorios del módulo 3 (app/src/main/assets/learning_labs.json)
--        y el objetivo es el título de ese objetivo en el laboratorio. Están DUPLICADOS respecto de ese JSON:
--        si alguien cambia un comando del laboratorio, hay que actualizarlo aquí en un script nuevo.
--
-- QUÉ NO HACE
--   * No crea tablas, funciones, políticas ni permisos, y no toca nada de 0008 ni de módulos anteriores. Las tres
--     tablas ya quedaron configuradas en 0008: battle_questions y ulti_challenges son CERRADAS (sin ningún
--     permiso para la app: las respuestas correctas y los comandos esperados nunca salen del servidor) y
--     fighter_level_curve solo se lee.
--   * No usa el valor "active" para nada especial: todas las filas quedan activas.
--
-- SEGURIDAD DE LA APLICACIÓN DEL SCRIPT
--   * Todo va dentro de begin/commit: si algo falla a medias, no queda ninguna fila (se deshace todo).
--   * Hay una comprobación previa: si las tres tablas NO están vacías, el script se detiene con un mensaje, para
--     que aplicarlo dos veces (o encima de datos de prueba) no deje preguntas duplicadas. Las tres tablas deben
--     estar vacías: el guion supabase/verificacion/0008_escenarios.sql las deja como estaban (vacías) al terminar.
--   * Este archivo no contiene claves, URLs ni datos personales.
--
-- REVISIÓN POR EL GRUPO: las 72 preguntas están en comentarios, legibles por tema, antes de cada bloque de INSERT.
-- Qué mirar: (1) una sola respuesta correcta por pregunta; (2) que el escalón de cada una sea el adecuado;
-- (3) español claro y con tildes; (4) los 12 retos; (5) si 12 por tema alcanzan (O12: ampliar luego con un script nuevo).
-- =====================================================================================================

begin;

-- -----------------------------------------------------------------------------------------------------
-- 0. Comprobación previa: las tres tablas deben estar vacías
-- -----------------------------------------------------------------------------------------------------

do $comprobacion$
begin
  if exists (select 1 from public.fighter_level_curve)
     or exists (select 1 from public.battle_questions)
     or exists (select 1 from public.ulti_challenges) then
    raise exception '0009: fighter_level_curve, battle_questions y ulti_challenges deben estar vacías (el script se aplica una sola vez)';
  end if;
end
$comprobacion$;

-- -----------------------------------------------------------------------------------------------------
-- 1. Curva de XP (O1): 100 XP por nivel, tope en el nivel 20 (1.900 XP)
--    nivel 1 = 0 XP, nivel 2 = 100 XP, ..., nivel 20 = 1.900 XP. Más allá del 20 está fuera de alcance (D8).
-- -----------------------------------------------------------------------------------------------------

insert into public.fighter_level_curve (level, min_xp)
select n, (n - 1) * 100
from generate_series(1, 20) as n;

-- -----------------------------------------------------------------------------------------------------
-- 2. Banco de preguntas (D9). Columnas: topic, tier, question, option_a a option_d, correct.
--    Límites de los check de 0008: pregunta hasta 400 caracteres, opción hasta 200, correct de A a D.
-- -----------------------------------------------------------------------------------------------------

-- ===== Tema 1: Contraseñas y Autenticación (CONTRASENAS) ==========================================
--  CON-01 (esc. 1) ¿Cuál de estas contraseñas es más segura?
--         A) 123456   B) Tu_Nombre2024   C) k7#mQ!9zRt$2   D) contraseña                      -> C
--  CON-02 (esc. 1) ¿Qué vuelve más difícil adivinar una contraseña?
--         A) Que sea corta y fácil de recordar   B) Que sea larga y mezcle letras, números y símbolos
--         C) Que use tu fecha de nacimiento   D) Que repita el mismo carácter                  -> B
--  CON-03 (esc. 1) ¿Qué debes hacer con tus contraseñas?
--         A) Compartirlas con amigos de confianza   B) Dejarlas anotadas junto al computador
--         C) Enviarlas por mensaje cuando alguien las pida   D) No compartirlas con nadie      -> D
--  CON-04 (esc. 1) ¿Qué te ayuda a manejar muchas contraseñas distintas de forma segura?
--         A) Un gestor de contraseñas   B) Usar la misma en todas las cuentas
--         C) Anotarlas en un papel pegado al monitor   D) Guardarlas en un chat con un amigo   -> A
--  CON-05 (esc. 2) ¿Por qué es mala idea reutilizar la misma contraseña en varios sitios?
--         A) Porque los sitios la rechazan   B) Porque si se filtra en un sitio, se puede probar en los demás
--         C) Porque se vuelve más lenta de escribir   D) Porque el navegador la borra          -> B
--  CON-06 (esc. 2) ¿Qué es la autenticación en dos pasos (2FA)?
--         A) Escribir la contraseña dos veces   B) Tener dos contraseñas iguales
--         C) Pedir un segundo factor, como un código, además de la contraseña
--         D) Cambiar la contraseña cada dos días                                               -> C
--  CON-07 (esc. 2) Un programa prueba una lista de contraseñas comunes contra una cuenta, una tras otra. ¿Cómo se llama el ataque?
--         A) Ataque de diccionario   B) Suplantación de identidad   C) Escaneo de puertos
--         D) Cifrado asimétrico                                                                -> A
--  CON-08 (esc. 2) ¿Cuál de estas es una mejor contraseña?
--         A) Password123   B) qwerty   C) NombreApellido   D) mi-gato-come-pan-azul-en-la-luna  -> D
--  CON-09 (esc. 3) ¿Cómo debería guardar un sitio bien diseñado las contraseñas de sus usuarios?
--         A) En texto plano, para poder recuperarlas   B) Codificadas en Base64
--         C) Cifradas con una clave que conoce todo el personal
--         D) Como un hash con sal, calculado con un algoritmo lento                            -> D
--  CON-10 (esc. 3) ¿Qué ventaja tiene una llave de seguridad física (FIDO2) frente a un código por SMS?
--         A) Resiste el phishing, porque solo responde al sitio legítimo   B) Es más barata
--         C) No necesita ningún dispositivo   D) Permite usar contraseñas más cortas           -> A
--  CON-11 (esc. 3) Un sitio te avisa de que tu contraseña apareció en una filtración. ¿Qué haces primero?
--         A) Esperar a ver si pasa algo   B) Cambiar solo una letra
--         C) Cambiarla ahí y en cualquier otro sitio donde la usaras   D) Borrar tu correo      -> C
--  CON-12 (esc. 3) ¿Qué es el "password spraying"?
--         A) Probar millones de contraseñas contra una sola cuenta
--         B) Probar unas pocas contraseñas comunes contra muchas cuentas para evitar bloqueos
--         C) Robar contraseñas con un teclado falso   D) Adivinar contraseñas con ingeniería social -> B
insert into public.battle_questions (topic, tier, question, option_a, option_b, option_c, option_d, correct) values
('CONTRASENAS', 1, '¿Cuál de estas contraseñas es más segura?', '123456', 'Tu_Nombre2024', 'k7#mQ!9zRt$2', 'contraseña', 'C'),
('CONTRASENAS', 1, '¿Qué vuelve más difícil adivinar una contraseña?', 'Que sea corta y fácil de recordar', 'Que sea larga y mezcle letras, números y símbolos', 'Que use tu fecha de nacimiento', 'Que repita el mismo carácter', 'B'),
('CONTRASENAS', 1, '¿Qué debes hacer con tus contraseñas?', 'Compartirlas con amigos de confianza', 'Dejarlas anotadas junto al computador', 'Enviarlas por mensaje cuando alguien las pida', 'No compartirlas con nadie', 'D'),
('CONTRASENAS', 1, '¿Qué te ayuda a manejar muchas contraseñas distintas de forma segura?', 'Un gestor de contraseñas', 'Usar la misma en todas las cuentas', 'Anotarlas en un papel pegado al monitor', 'Guardarlas en un chat con un amigo', 'A'),
('CONTRASENAS', 2, '¿Por qué es mala idea reutilizar la misma contraseña en varios sitios?', 'Porque los sitios la rechazan', 'Porque si se filtra en un sitio, se puede probar en los demás', 'Porque se vuelve más lenta de escribir', 'Porque el navegador la borra', 'B'),
('CONTRASENAS', 2, '¿Qué es la autenticación en dos pasos (2FA)?', 'Escribir la contraseña dos veces', 'Tener dos contraseñas iguales', 'Pedir un segundo factor, como un código, además de la contraseña', 'Cambiar la contraseña cada dos días', 'C'),
('CONTRASENAS', 2, 'Un programa prueba una lista de contraseñas comunes contra una cuenta, una tras otra. ¿Cómo se llama el ataque?', 'Ataque de diccionario', 'Suplantación de identidad', 'Escaneo de puertos', 'Cifrado asimétrico', 'A'),
('CONTRASENAS', 2, '¿Cuál de estas es una mejor contraseña?', 'Password123', 'qwerty', 'NombreApellido', 'mi-gato-come-pan-azul-en-la-luna', 'D'),
('CONTRASENAS', 3, '¿Cómo debería guardar un sitio bien diseñado las contraseñas de sus usuarios?', 'En texto plano, para poder recuperarlas', 'Codificadas en Base64', 'Cifradas con una clave que conoce todo el personal', 'Como un hash con sal, calculado con un algoritmo lento', 'D'),
('CONTRASENAS', 3, '¿Qué ventaja tiene una llave de seguridad física (FIDO2) frente a un código por SMS?', 'Resiste el phishing, porque solo responde al sitio legítimo', 'Es más barata', 'No necesita ningún dispositivo', 'Permite usar contraseñas más cortas', 'A'),
('CONTRASENAS', 3, 'Un sitio te avisa de que tu contraseña apareció en una filtración. ¿Qué haces primero?', 'Esperar a ver si pasa algo', 'Cambiar solo una letra', 'Cambiarla ahí y en cualquier otro sitio donde la usaras', 'Borrar tu correo', 'C'),
('CONTRASENAS', 3, '¿Qué es el "password spraying"?', 'Probar millones de contraseñas contra una sola cuenta', 'Probar unas pocas contraseñas comunes contra muchas cuentas para evitar bloqueos', 'Robar contraseñas con un teclado falso', 'Adivinar contraseñas con ingeniería social', 'B');

-- ===== Tema 2: Phishing (PHISHING) ================================================================
--  PHI-01 (esc. 1) ¿Qué es el phishing?
--         A) Un virus que borra archivos
--         B) Un engaño para que entregues datos o dinero haciéndose pasar por alguien de confianza
--         C) Un tipo de contraseña   D) Un programa para limpiar el correo                      -> B
--  PHI-02 (esc. 1) Un correo de "tu banco" dice que tu cuenta se bloqueará en una hora si no entras a un enlace. ¿Qué señal de alerta tiene?
--         A) Crea urgencia para que actúes sin pensar   B) Está escrito en español
--         C) Menciona un banco   D) Trae el logo del banco                                      -> A
--  PHI-03 (esc. 1) ¿Qué haces antes de tocar un enlace sospechoso?
--         A) Tocarlo para ver adónde lleva   B) Reenviarlo a tus contactos   C) Abrirlo con otra cuenta
--         D) Revisar la dirección real del enlace (por ejemplo, manteniéndolo presionado)        -> D
--  PHI-04 (esc. 1) ¿Qué haces con un correo que parece phishing?
--         A) Responder pidiendo explicaciones   B) Reenviarlo como aviso
--         C) No abrir enlaces ni adjuntos, y reportarlo o eliminarlo
--         D) Descargar el adjunto en otro equipo                                               -> C
--  PHI-05 (esc. 2) ¿Cuál de estas direcciones parece una copia falsa de banco-ejemplo.com?
--         A) app.banco-ejemplo.com   B) www.banco-ejemplo.com   C) banco-ejemp1o.com   D) banco-ejemplo.com -> C
--  PHI-06 (esc. 2) ¿Qué es el "spear phishing"?
--         A) Un phishing masivo sin destinatario concreto
--         B) Un phishing dirigido a una persona concreta, con datos suyos para parecer real
--         C) Un phishing por llamada   D) Un filtro antispam                                    -> B
--  PHI-07 (esc. 2) ¿Qué es el "smishing"?
--         A) Phishing por mensajes de texto (SMS)   B) Phishing por llamada de voz
--         C) Phishing por correo   D) Phishing por códigos QR                                  -> A
--  PHI-08 (esc. 2) Un adjunto se llama "factura.pdf.exe". ¿Qué indica?
--         A) Es un PDF normal   B) Es un PDF protegido con contraseña   C) Es un archivo comprimido
--         D) Es un programa ejecutable disfrazado de PDF                                       -> D
--  PHI-09 (esc. 3) ¿Qué es el fraude BEC (Business Email Compromise)?
--         A) Alguien se hace pasar por un directivo o proveedor para pedir pagos o datos
--         B) Un virus que se propaga por correo   C) Un cifrado para correos   D) Un servidor de correo -> A
--  PHI-10 (esc. 3) ¿Qué mecanismos ayudan a comprobar que un correo viene realmente del dominio que dice?
--         A) HTTP, FTP y SMTP   B) WPA2, WEP y WPS   C) RAID, NAS y SAN   D) SPF, DKIM y DMARC   -> D
--  PHI-11 (esc. 3) Una página falsa muestra el candado de HTTPS. ¿Qué significa eso?
--         A) Que el sitio es legítimo
--         B) Que la conexión va cifrada, pero no prueba que el sitio sea quien dice ser
--         C) Que el sitio no tiene virus   D) Que el gobierno lo aprobó                         -> B
--  PHI-12 (esc. 3) Un gestor de contraseñas no rellena tu contraseña en un sitio que parece el de siempre. ¿Por qué eso ayuda contra el phishing?
--         A) Porque cifra el sitio   B) Porque bloquea los correos falsos
--         C) Porque solo autocompleta en el dominio guardado, así que la falta de relleno es una alerta
--         D) Porque cambia la contraseña sola                                                  -> C
insert into public.battle_questions (topic, tier, question, option_a, option_b, option_c, option_d, correct) values
('PHISHING', 1, '¿Qué es el phishing?', 'Un virus que borra archivos', 'Un engaño para que entregues datos o dinero haciéndose pasar por alguien de confianza', 'Un tipo de contraseña', 'Un programa para limpiar el correo', 'B'),
('PHISHING', 1, 'Un correo de "tu banco" dice que tu cuenta se bloqueará en una hora si no entras a un enlace. ¿Qué señal de alerta tiene?', 'Crea urgencia para que actúes sin pensar', 'Está escrito en español', 'Menciona un banco', 'Trae el logo del banco', 'A'),
('PHISHING', 1, '¿Qué haces antes de tocar un enlace sospechoso?', 'Tocarlo para ver adónde lleva', 'Reenviarlo a tus contactos', 'Abrirlo con otra cuenta', 'Revisar la dirección real del enlace (por ejemplo, manteniéndolo presionado)', 'D'),
('PHISHING', 1, '¿Qué haces con un correo que parece phishing?', 'Responder pidiendo explicaciones', 'Reenviarlo como aviso', 'No abrir enlaces ni adjuntos, y reportarlo o eliminarlo', 'Descargar el adjunto en otro equipo', 'C'),
('PHISHING', 2, '¿Cuál de estas direcciones parece una copia falsa de banco-ejemplo.com?', 'app.banco-ejemplo.com', 'www.banco-ejemplo.com', 'banco-ejemp1o.com', 'banco-ejemplo.com', 'C'),
('PHISHING', 2, '¿Qué es el "spear phishing"?', 'Un phishing masivo sin destinatario concreto', 'Un phishing dirigido a una persona concreta, con datos suyos para parecer real', 'Un phishing por llamada', 'Un filtro antispam', 'B'),
('PHISHING', 2, '¿Qué es el "smishing"?', 'Phishing por mensajes de texto (SMS)', 'Phishing por llamada de voz', 'Phishing por correo', 'Phishing por códigos QR', 'A'),
('PHISHING', 2, 'Un adjunto se llama "factura.pdf.exe". ¿Qué indica?', 'Es un PDF normal', 'Es un PDF protegido con contraseña', 'Es un archivo comprimido', 'Es un programa ejecutable disfrazado de PDF', 'D'),
('PHISHING', 3, '¿Qué es el fraude BEC (Business Email Compromise)?', 'Alguien se hace pasar por un directivo o proveedor para pedir pagos o datos', 'Un virus que se propaga por correo', 'Un cifrado para correos', 'Un servidor de correo', 'A'),
('PHISHING', 3, '¿Qué mecanismos ayudan a comprobar que un correo viene realmente del dominio que dice?', 'HTTP, FTP y SMTP', 'WPA2, WEP y WPS', 'RAID, NAS y SAN', 'SPF, DKIM y DMARC', 'D'),
('PHISHING', 3, 'Una página falsa muestra el candado de HTTPS. ¿Qué significa eso?', 'Que el sitio es legítimo', 'Que la conexión va cifrada, pero no prueba que el sitio sea quien dice ser', 'Que el sitio no tiene virus', 'Que el gobierno lo aprobó', 'B'),
('PHISHING', 3, 'Un gestor de contraseñas no rellena tu contraseña en un sitio que parece el de siempre. ¿Por qué eso ayuda contra el phishing?', 'Porque cifra el sitio', 'Porque bloquea los correos falsos', 'Porque solo autocompleta en el dominio guardado, así que la falta de relleno es una alerta', 'Porque cambia la contraseña sola', 'C');

-- ===== Tema 3: Malware (MALWARE) ==================================================================
--  MAL-01 (esc. 1) ¿Qué es el malware?
--         A) Software hecho para dañar, espiar o tomar el control de un equipo   B) Un tipo de hardware
--         C) Una red wifi   D) Un navegador                                                    -> A
--  MAL-02 (esc. 1) ¿Qué hace el ransomware?
--         A) Acelera el equipo   B) Cifra tus archivos y pide un pago para devolverlos
--         C) Mejora el antivirus   D) Borra el historial del navegador                          -> B
--  MAL-03 (esc. 1) ¿Cuál es una buena forma de reducir el riesgo de malware?
--         A) Abrir todos los adjuntos   B) Descargar programas "crackeados"
--         C) Instalar aplicaciones solo de tiendas oficiales y mantener todo actualizado
--         D) Desactivar el antivirus para ir más rápido                                        -> C
--  MAL-04 (esc. 1) ¿Cuál de estos es un ejemplo de malware?
--         A) Un procesador de texto   B) Un controlador de impresora   C) Un salvapantallas oficial
--         D) Un programa espía (spyware)                                                       -> D
--  MAL-05 (esc. 2) ¿Qué es un troyano?
--         A) Un virus que se copia solo por la red   B) Un tipo de antivirus   C) Un tipo de firewall
--         D) Un programa que se hace pasar por legítimo para colar algo dañino                  -> D
--  MAL-06 (esc. 2) ¿Qué distingue a un gusano (worm)?
--         A) Se propaga por sí solo por la red, sin que alguien ejecute un archivo
--         B) Solo afecta a equipos Mac   C) Solo borra imágenes   D) Necesita siempre un USB     -> A
--  MAL-07 (esc. 2) ¿Qué hace un keylogger?
--         A) Acelera el teclado   B) Registra lo que escribes   C) Cambia el idioma del teclado
--         D) Cifra el disco                                                                    -> B
--  MAL-08 (esc. 2) Sospechas que tu equipo tiene malware. ¿Qué haces primero?
--         A) Seguir usándolo con normalidad   B) Pagar a quien te lo pida
--         C) Desconectarlo de la red y analizarlo con un antivirus actualizado
--         D) Reiniciar y esperar                                                               -> C
--  MAL-09 (esc. 3) ¿Qué es una botnet?
--         A) Una red wifi pública   B) Un tipo de VPN
--         C) Una red de equipos infectados que un atacante controla a distancia
--         D) Una base de datos de contraseñas                                                  -> C
--  MAL-10 (esc. 3) ¿Qué es un rootkit?
--         A) Un kit de herramientas legítimo para administradores   B) Un tipo de phishing
--         C) Un cifrador de archivos
--         D) Malware que oculta su presencia y conserva acceso privilegiado                    -> D
--  MAL-11 (esc. 3) ¿Por qué ayuda contra el ransomware una copia de seguridad desconectada de la red (offline)?
--         A) Porque el ransomware no puede cifrarla   B) Porque es más rápida
--         C) Porque está en la misma carpeta   D) Porque evita infectarse                       -> A
--  MAL-12 (esc. 3) ¿Para qué sirve analizar un archivo sospechoso en una "sandbox"?
--         A) Para borrarlo
--         B) Para ejecutarlo en un entorno aislado y observar su comportamiento sin riesgo para el equipo
--         C) Para comprimirlo   D) Para enviarlo por correo                                    -> B
insert into public.battle_questions (topic, tier, question, option_a, option_b, option_c, option_d, correct) values
('MALWARE', 1, '¿Qué es el malware?', 'Software hecho para dañar, espiar o tomar el control de un equipo', 'Un tipo de hardware', 'Una red wifi', 'Un navegador', 'A'),
('MALWARE', 1, '¿Qué hace el ransomware?', 'Acelera el equipo', 'Cifra tus archivos y pide un pago para devolverlos', 'Mejora el antivirus', 'Borra el historial del navegador', 'B'),
('MALWARE', 1, '¿Cuál es una buena forma de reducir el riesgo de malware?', 'Abrir todos los adjuntos', 'Descargar programas "crackeados"', 'Instalar aplicaciones solo de tiendas oficiales y mantener todo actualizado', 'Desactivar el antivirus para ir más rápido', 'C'),
('MALWARE', 1, '¿Cuál de estos es un ejemplo de malware?', 'Un procesador de texto', 'Un controlador de impresora', 'Un salvapantallas oficial', 'Un programa espía (spyware)', 'D'),
('MALWARE', 2, '¿Qué es un troyano?', 'Un virus que se copia solo por la red', 'Un tipo de antivirus', 'Un tipo de firewall', 'Un programa que se hace pasar por legítimo para colar algo dañino', 'D'),
('MALWARE', 2, '¿Qué distingue a un gusano (worm)?', 'Se propaga por sí solo por la red, sin que alguien ejecute un archivo', 'Solo afecta a equipos Mac', 'Solo borra imágenes', 'Necesita siempre un USB', 'A'),
('MALWARE', 2, '¿Qué hace un keylogger?', 'Acelera el teclado', 'Registra lo que escribes', 'Cambia el idioma del teclado', 'Cifra el disco', 'B'),
('MALWARE', 2, 'Sospechas que tu equipo tiene malware. ¿Qué haces primero?', 'Seguir usándolo con normalidad', 'Pagar a quien te lo pida', 'Desconectarlo de la red y analizarlo con un antivirus actualizado', 'Reiniciar y esperar', 'C'),
('MALWARE', 3, '¿Qué es una botnet?', 'Una red wifi pública', 'Un tipo de VPN', 'Una red de equipos infectados que un atacante controla a distancia', 'Una base de datos de contraseñas', 'C'),
('MALWARE', 3, '¿Qué es un rootkit?', 'Un kit de herramientas legítimo para administradores', 'Un tipo de phishing', 'Un cifrador de archivos', 'Malware que oculta su presencia y conserva acceso privilegiado', 'D'),
('MALWARE', 3, '¿Por qué ayuda contra el ransomware una copia de seguridad desconectada de la red (offline)?', 'Porque el ransomware no puede cifrarla', 'Porque es más rápida', 'Porque está en la misma carpeta', 'Porque evita infectarse', 'A'),
('MALWARE', 3, '¿Para qué sirve analizar un archivo sospechoso en una "sandbox"?', 'Para borrarlo', 'Para ejecutarlo en un entorno aislado y observar su comportamiento sin riesgo para el equipo', 'Para comprimirlo', 'Para enviarlo por correo', 'B');

-- ===== Tema 4: Ingeniería Social (INGENIERIA_SOCIAL) ==============================================
--  ISO-01 (esc. 1) ¿Qué es la ingeniería social?
--         A) Programar redes sociales   B) Manipular a las personas para obtener información o acceso
--         C) Un curso de redes   D) Un tipo de cifrado                                          -> B
--  ISO-02 (esc. 1) ¿Qué dato no te pediría nunca un soporte técnico legítimo?
--         A) El número de ticket   B) El nombre del equipo   C) Una descripción del problema
--         D) Tu contraseña                                                                     -> D
--  ISO-03 (esc. 1) ¿Qué es el "pretexting"?
--         A) Inventar una historia creíble para ganar tu confianza y sacarte datos
--         B) Un tipo de malware   C) Un protocolo de red   D) Una copia de seguridad            -> A
--  ISO-04 (esc. 1) ¿Qué es el "tailgating"?
--         A) Un virus   B) Un cifrado
--         C) Entrar a una zona restringida siguiendo de cerca a alguien autorizado   D) Una VPN -> C
--  ISO-05 (esc. 2) Alguien deja memorias USB "perdidas" en un estacionamiento para que las conecten. ¿Qué técnica es?
--         A) Baiting (cebo)   B) Smishing   C) Spoofing   D) Doxing                             -> A
--  ISO-06 (esc. 2) Un mensaje dice: "Solo te quedan 5 minutos para reclamar tu premio". ¿Qué principio de manipulación usa?
--         A) Autoridad   B) Reciprocidad   C) Urgencia   D) Simpatía                            -> C
--  ISO-07 (esc. 2) Un "jefe" te escribe desde un correo nuevo y te pide comprar tarjetas de regalo con urgencia. ¿Qué haces?
--         A) Comprarlas rápido   B) Verificar el pedido por otro canal que ya conozcas
--         C) Responder con tus datos   D) Reenviarlo a un compañero                             -> B
--  ISO-08 (esc. 2) ¿Qué es el "shoulder surfing"?
--         A) Un deporte acuático   B) Un malware   C) Un tipo de red
--         D) Mirar por encima del hombro para ver contraseñas o pantallas                      -> D
--  ISO-09 (esc. 3) ¿Qué es el "vishing"?
--         A) Phishing por SMS   B) Phishing por llamada de voz   C) Phishing por QR   D) Phishing por correo -> B
--  ISO-10 (esc. 3) ¿Qué es el "quishing"?
--         A) Phishing con códigos QR maliciosos   B) Phishing por voz   C) Un ransomware   D) Un cifrado -> A
--  ISO-11 (esc. 3) ¿Qué información pública de tus redes sociales podría ayudar a un atacante a adivinar tus preguntas de seguridad?
--         A) El idioma de tu teléfono   B) La hora a la que publicas   C) El modelo de tu teléfono
--         D) El nombre de tu mascota, tu ciudad natal o tu colegio                             -> D
--  ISO-12 (esc. 3) ¿Cuál es la mejor defensa general contra la ingeniería social?
--         A) Un antivirus más potente   B) Una contraseña más larga
--         C) Desconfiar de pedidos urgentes o inusuales y confirmarlos por otro canal
--         D) Un firewall más caro                                                              -> C
insert into public.battle_questions (topic, tier, question, option_a, option_b, option_c, option_d, correct) values
('INGENIERIA_SOCIAL', 1, '¿Qué es la ingeniería social?', 'Programar redes sociales', 'Manipular a las personas para obtener información o acceso', 'Un curso de redes', 'Un tipo de cifrado', 'B'),
('INGENIERIA_SOCIAL', 1, '¿Qué dato no te pediría nunca un soporte técnico legítimo?', 'El número de ticket', 'El nombre del equipo', 'Una descripción del problema', 'Tu contraseña', 'D'),
('INGENIERIA_SOCIAL', 1, '¿Qué es el "pretexting"?', 'Inventar una historia creíble para ganar tu confianza y sacarte datos', 'Un tipo de malware', 'Un protocolo de red', 'Una copia de seguridad', 'A'),
('INGENIERIA_SOCIAL', 1, '¿Qué es el "tailgating"?', 'Un virus', 'Un cifrado', 'Entrar a una zona restringida siguiendo de cerca a alguien autorizado', 'Una VPN', 'C'),
('INGENIERIA_SOCIAL', 2, 'Alguien deja memorias USB "perdidas" en un estacionamiento para que las conecten. ¿Qué técnica es?', 'Baiting (cebo)', 'Smishing', 'Spoofing', 'Doxing', 'A'),
('INGENIERIA_SOCIAL', 2, 'Un mensaje dice: "Solo te quedan 5 minutos para reclamar tu premio". ¿Qué principio de manipulación usa?', 'Autoridad', 'Reciprocidad', 'Urgencia', 'Simpatía', 'C'),
('INGENIERIA_SOCIAL', 2, 'Un "jefe" te escribe desde un correo nuevo y te pide comprar tarjetas de regalo con urgencia. ¿Qué haces?', 'Comprarlas rápido', 'Verificar el pedido por otro canal que ya conozcas', 'Responder con tus datos', 'Reenviarlo a un compañero', 'B'),
('INGENIERIA_SOCIAL', 2, '¿Qué es el "shoulder surfing"?', 'Un deporte acuático', 'Un malware', 'Un tipo de red', 'Mirar por encima del hombro para ver contraseñas o pantallas', 'D'),
('INGENIERIA_SOCIAL', 3, '¿Qué es el "vishing"?', 'Phishing por SMS', 'Phishing por llamada de voz', 'Phishing por QR', 'Phishing por correo', 'B'),
('INGENIERIA_SOCIAL', 3, '¿Qué es el "quishing"?', 'Phishing con códigos QR maliciosos', 'Phishing por voz', 'Un ransomware', 'Un cifrado', 'A'),
('INGENIERIA_SOCIAL', 3, '¿Qué información pública de tus redes sociales podría ayudar a un atacante a adivinar tus preguntas de seguridad?', 'El idioma de tu teléfono', 'La hora a la que publicas', 'El modelo de tu teléfono', 'El nombre de tu mascota, tu ciudad natal o tu colegio', 'D'),
('INGENIERIA_SOCIAL', 3, '¿Cuál es la mejor defensa general contra la ingeniería social?', 'Un antivirus más potente', 'Una contraseña más larga', 'Desconfiar de pedidos urgentes o inusuales y confirmarlos por otro canal', 'Un firewall más caro', 'C');

-- ===== Tema 5: Redes Seguras (REDES_SEGURAS) ======================================================
--  RED-01 (esc. 1) ¿Qué es más seguro en tu wifi doméstica?
--         A) Dejarla sin contraseña   B) Usar WEP   C) Poner tus datos personales en el nombre de la red
--         D) WPA3 (o WPA2) con una contraseña robusta                                          -> D
--  RED-02 (esc. 1) ¿Qué riesgo tiene una wifi pública abierta?
--         A) Gasta más batería   B) Es más lenta   C) Alguien puede espiar el tráfico que no esté cifrado
--         D) Ninguno                                                                           -> C
--  RED-03 (esc. 1) ¿Qué hace una VPN?
--         A) Acelera internet   B) Crea un túnel cifrado para tu tráfico   C) Elimina los virus
--         D) Reemplaza al antivirus                                                            -> B
--  RED-04 (esc. 1) ¿Qué deberías cambiar en un router nuevo?
--         A) La contraseña de administración que trae de fábrica   B) El color   C) La antena
--         D) Nada: ya viene seguro                                                             -> A
--  RED-05 (esc. 2) ¿Qué indica "https://" al inicio de una dirección?
--         A) Que el sitio es del gobierno   B) Que la conexión con el sitio va cifrada
--         C) Que el sitio no tiene anuncios   D) Que el sitio es oficial                       -> B
--  RED-06 (esc. 2) ¿Qué es un ataque "man-in-the-middle"?
--         A) Un ataque que borra discos   B) Un tipo de VPN   C) Una copia de seguridad
--         D) Un atacante que se interpone entre dos partes y puede leer o alterar lo que se envían -> D
--  RED-07 (esc. 2) ¿Para qué sirve tener una red de invitados separada en tu router?
--         A) Para que un dispositivo comprometido de un invitado no alcance tus equipos
--         B) Para ir más rápido   C) Para usar menos cables   D) Para ocultar el nombre de la red -> A
--  RED-08 (esc. 2) ¿Qué es WPS en un router y qué conviene hacer con él?
--         A) Un cifrado muy fuerte; conviene dejarlo activo   B) Un antivirus del router; conviene actualizarlo
--         C) Un emparejamiento por PIN con debilidades conocidas; conviene desactivarlo
--         D) Una VPN integrada; conviene activarla siempre                                     -> C
--  RED-09 (esc. 3) ¿Qué es el "DNS spoofing"?
--         A) Un escaneo de puertos   B) Un cifrado de disco
--         C) Falsear respuestas de DNS para llevarte a un sitio falso   D) Una copia de seguridad -> C
--  RED-10 (esc. 3) ¿Qué hace un "evil twin"?
--         A) Duplica tus archivos
--         B) Imita una red wifi legítima para capturar el tráfico de quien se conecta
--         C) Es un tipo de switch   D) Es una VPN gratuita                                      -> B
--  RED-11 (esc. 3) ¿Qué protocolo reemplaza a Telnet con comunicación cifrada para administrar equipos a distancia?
--         A) FTP   B) HTTP   C) SMTP   D) SSH                                                   -> D
--  RED-12 (esc. 3) ¿Para qué sirve un escaneo de puertos con una herramienta como nmap en tu propia red?
--         A) Para descubrir qué servicios están expuestos   B) Para aumentar la velocidad
--         C) Para cifrar el tráfico   D) Para borrar registros                                  -> A
insert into public.battle_questions (topic, tier, question, option_a, option_b, option_c, option_d, correct) values
('REDES_SEGURAS', 1, '¿Qué es más seguro en tu wifi doméstica?', 'Dejarla sin contraseña', 'Usar WEP', 'Poner tus datos personales en el nombre de la red', 'WPA3 (o WPA2) con una contraseña robusta', 'D'),
('REDES_SEGURAS', 1, '¿Qué riesgo tiene una wifi pública abierta?', 'Gasta más batería', 'Es más lenta', 'Alguien puede espiar el tráfico que no esté cifrado', 'Ninguno', 'C'),
('REDES_SEGURAS', 1, '¿Qué hace una VPN?', 'Acelera internet', 'Crea un túnel cifrado para tu tráfico', 'Elimina los virus', 'Reemplaza al antivirus', 'B'),
('REDES_SEGURAS', 1, '¿Qué deberías cambiar en un router nuevo?', 'La contraseña de administración que trae de fábrica', 'El color', 'La antena', 'Nada: ya viene seguro', 'A'),
('REDES_SEGURAS', 2, '¿Qué indica "https://" al inicio de una dirección?', 'Que el sitio es del gobierno', 'Que la conexión con el sitio va cifrada', 'Que el sitio no tiene anuncios', 'Que el sitio es oficial', 'B'),
('REDES_SEGURAS', 2, '¿Qué es un ataque "man-in-the-middle"?', 'Un ataque que borra discos', 'Un tipo de VPN', 'Una copia de seguridad', 'Un atacante que se interpone entre dos partes y puede leer o alterar lo que se envían', 'D'),
('REDES_SEGURAS', 2, '¿Para qué sirve tener una red de invitados separada en tu router?', 'Para que un dispositivo comprometido de un invitado no alcance tus equipos', 'Para ir más rápido', 'Para usar menos cables', 'Para ocultar el nombre de la red', 'A'),
('REDES_SEGURAS', 2, '¿Qué es WPS en un router y qué conviene hacer con él?', 'Un cifrado muy fuerte; conviene dejarlo activo', 'Un antivirus del router; conviene actualizarlo', 'Un emparejamiento por PIN con debilidades conocidas; conviene desactivarlo', 'Una VPN integrada; conviene activarla siempre', 'C'),
('REDES_SEGURAS', 3, '¿Qué es el "DNS spoofing"?', 'Un escaneo de puertos', 'Un cifrado de disco', 'Falsear respuestas de DNS para llevarte a un sitio falso', 'Una copia de seguridad', 'C'),
('REDES_SEGURAS', 3, '¿Qué hace un "evil twin"?', 'Duplica tus archivos', 'Imita una red wifi legítima para capturar el tráfico de quien se conecta', 'Es un tipo de switch', 'Es una VPN gratuita', 'B'),
('REDES_SEGURAS', 3, '¿Qué protocolo reemplaza a Telnet con comunicación cifrada para administrar equipos a distancia?', 'FTP', 'HTTP', 'SMTP', 'SSH', 'D'),
('REDES_SEGURAS', 3, '¿Para qué sirve un escaneo de puertos con una herramienta como nmap en tu propia red?', 'Para descubrir qué servicios están expuestos', 'Para aumentar la velocidad', 'Para cifrar el tráfico', 'Para borrar registros', 'A');

-- ===== Tema 6: Firewall (FIREWALL) ================================================================
--  FIR-01 (esc. 1) ¿Dónde puede estar un firewall?
--         A) Solo en un router   B) Solo en la nube
--         C) Como programa en un equipo o como dispositivo en la red   D) Solo en el navegador   -> C
--  FIR-02 (esc. 1) ¿Qué significa "denegar por defecto"?
--         A) Bloquear todo lo que no esté permitido de forma explícita
--         B) Permitir todo salvo lo prohibido   C) Borrar todas las reglas   D) Apagar el firewall -> A
--  FIR-03 (esc. 1) ¿Qué es un puerto de red?
--         A) Un cable   B) Un número que identifica un servicio en un equipo (por ejemplo, 443 para HTTPS)
--         C) Un tipo de virus   D) Una contraseña                                               -> B
--  FIR-04 (esc. 1) Un programa desconocido te pide que desactives el firewall para instalarse. ¿Qué haces?
--         A) Desactivarlo   B) Desactivarlo solo un rato   C) Reiniciar el equipo
--         D) No hacerlo y revisar de dónde viene el programa                                   -> D
--  FIR-05 (esc. 2) ¿Qué comando de ufw bloquea el tráfico que viene de la IP 198.51.100.9?
--         A) ufw deny from 198.51.100.9   B) ufw allow from 198.51.100.9   C) ufw status   D) ufw reset -> A
--  FIR-06 (esc. 2) ¿Qué puerto usa HTTPS por defecto?
--         A) 21   B) 443   C) 25   D) 80                                                       -> B
--  FIR-07 (esc. 2) ¿Qué diferencia a un firewall "con estado" (stateful)?
--         A) Solo mira la dirección de destino   B) Solo funciona con wifi
--         C) Bloquea todo el tráfico   D) Sigue el estado de las conexiones, no solo cada paquete suelto -> D
--  FIR-08 (esc. 2) ¿Qué hace la regla "ufw allow 22/tcp"?
--         A) Bloquea el puerto 22   B) Borra la regla del puerto 22
--         C) Permite conexiones entrantes al puerto 22 (SSH)   D) Cambia el puerto de SSH       -> C
--  FIR-09 (esc. 3) ¿Qué es una DMZ en una red?
--         A) Una copia de seguridad   B) Una red wifi de invitados sin cifrado   C) Un antivirus de red
--         D) Una zona separada donde viven los servicios expuestos a Internet, aislada de la red interna -> D
--  FIR-10 (esc. 3) ¿Por qué importa el orden de las reglas de un firewall?
--         A) No importa   B) Se aplican todas a la vez
--         C) Se evalúan en orden y la primera que coincide suele ser la que se aplica
--         D) Solo cuenta la última                                                             -> C
--  FIR-11 (esc. 3) ¿Qué diferencia hay entre DROP y REJECT en un firewall?
--         A) DROP descarta el paquete en silencio; REJECT avisa al origen de que fue rechazado
--         B) DROP avisa al origen; REJECT no   C) Son lo mismo   D) DROP abre el puerto; REJECT lo cierra -> A
--  FIR-12 (esc. 3) ¿Qué hace un WAF (Web Application Firewall)?
--         A) Cifra los discos
--         B) Filtra el tráfico HTTP hacia una aplicación web para frenar ataques como la inyección SQL o el XSS
--         C) Reemplaza al antivirus   D) Reparte el wifi                                        -> B
insert into public.battle_questions (topic, tier, question, option_a, option_b, option_c, option_d, correct) values
('FIREWALL', 1, '¿Dónde puede estar un firewall?', 'Solo en un router', 'Solo en la nube', 'Como programa en un equipo o como dispositivo en la red', 'Solo en el navegador', 'C'),
('FIREWALL', 1, '¿Qué significa "denegar por defecto"?', 'Bloquear todo lo que no esté permitido de forma explícita', 'Permitir todo salvo lo prohibido', 'Borrar todas las reglas', 'Apagar el firewall', 'A'),
('FIREWALL', 1, '¿Qué es un puerto de red?', 'Un cable', 'Un número que identifica un servicio en un equipo (por ejemplo, 443 para HTTPS)', 'Un tipo de virus', 'Una contraseña', 'B'),
('FIREWALL', 1, 'Un programa desconocido te pide que desactives el firewall para instalarse. ¿Qué haces?', 'Desactivarlo', 'Desactivarlo solo un rato', 'Reiniciar el equipo', 'No hacerlo y revisar de dónde viene el programa', 'D'),
('FIREWALL', 2, '¿Qué comando de ufw bloquea el tráfico que viene de la IP 198.51.100.9?', 'ufw deny from 198.51.100.9', 'ufw allow from 198.51.100.9', 'ufw status', 'ufw reset', 'A'),
('FIREWALL', 2, '¿Qué puerto usa HTTPS por defecto?', '21', '443', '25', '80', 'B'),
('FIREWALL', 2, '¿Qué diferencia a un firewall "con estado" (stateful)?', 'Solo mira la dirección de destino', 'Solo funciona con wifi', 'Bloquea todo el tráfico', 'Sigue el estado de las conexiones, no solo cada paquete suelto', 'D'),
('FIREWALL', 2, '¿Qué hace la regla "ufw allow 22/tcp"?', 'Bloquea el puerto 22', 'Borra la regla del puerto 22', 'Permite conexiones entrantes al puerto 22 (SSH)', 'Cambia el puerto de SSH', 'C'),
('FIREWALL', 3, '¿Qué es una DMZ en una red?', 'Una copia de seguridad', 'Una red wifi de invitados sin cifrado', 'Un antivirus de red', 'Una zona separada donde viven los servicios expuestos a Internet, aislada de la red interna', 'D'),
('FIREWALL', 3, '¿Por qué importa el orden de las reglas de un firewall?', 'No importa', 'Se aplican todas a la vez', 'Se evalúan en orden y la primera que coincide suele ser la que se aplica', 'Solo cuenta la última', 'C'),
('FIREWALL', 3, '¿Qué diferencia hay entre DROP y REJECT en un firewall?', 'DROP descarta el paquete en silencio; REJECT avisa al origen de que fue rechazado', 'DROP avisa al origen; REJECT no', 'Son lo mismo', 'DROP abre el puerto; REJECT lo cierra', 'A'),
('FIREWALL', 3, '¿Qué hace un WAF (Web Application Firewall)?', 'Cifra los discos', 'Filtra el tráfico HTTP hacia una aplicación web para frenar ataques como la inyección SQL o el XSS', 'Reemplaza al antivirus', 'Reparte el wifi', 'B');

-- -----------------------------------------------------------------------------------------------------
-- 3. Retos de ulti (D4, O10): 6 de ataque y 6 de defensa, uno de cada tipo por tema, de dos conjuntos
--    separados (el reto de ataque y el de defensa de una misma ulti nunca coinciden). En pantalla solo se
--    muestra objective_title; el comando se sabe de memoria. expected_command es EXACTO (los espacios y las
--    mayúsculas de los argumentos cuentan; la primera palabra no distingue mayúsculas, ver sx_normalize_command).
--    Cada fila corresponde a un objetivo real de un laboratorio del módulo 3 (learning_labs.json):
--      ULT-A1 Lab Contraseñas, objetivo 1   ULT-D1 Lab Contraseñas, objetivo 3
--      ULT-A2 Lab Phishing, objetivo 1      ULT-D2 Lab Phishing, objetivo 3
--      ULT-A3 Lab Malware, objetivo 1       ULT-D3 Lab Malware, objetivo 3
--      ULT-A4 Lab Ingeniería Social, obj. 1 ULT-D4 Lab Ingeniería Social, obj. 3
--      ULT-A5 Lab Redes Seguras, objetivo 3 ULT-D5 Lab Redes Seguras, objetivo 1
--      ULT-A6 Lab Firewall, objetivo 1      ULT-D6 Lab Firewall, objetivo 3
-- -----------------------------------------------------------------------------------------------------

insert into public.ulti_challenges (kind, topic, objective_title, expected_command) values
('attack',  'CONTRASENAS',       'Probar una contraseña débil',               'crack 123456'),
('attack',  'PHISHING',          'Inspeccionar el remitente',                 'whois nightwatch-secure-verify.com'),
('attack',  'MALWARE',           'Escanear el archivo con el antivirus',      'scan sospechoso.exe'),
('attack',  'INGENIERIA_SOCIAL', 'Verificar la identidad del llamante',       'lookup +1-555-0134'),
('attack',  'REDES_SEGURAS',     'Investigar la IP sospechosa',               'whois 203.0.113.55'),
('attack',  'FIREWALL',          'Bloquear el SSH expuesto',                  'ufw deny 203.0.113.77 port 22'),
('defense', 'CONTRASENAS',       'Probar una contraseña robusta',             'crack N!ghtW4tch#7kQ2'),
('defense', 'PHISHING',          'Comparar con el dominio oficial',           'diff nightwatch-verify.io nw.app'),
('defense', 'MALWARE',           'Poner el archivo en cuarentena',            'quarantine sospechoso.exe'),
('defense', 'INGENIERIA_SOCIAL', 'Reportar el intento de ingeniería social',  'report-incident social-engineering'),
('defense', 'REDES_SEGURAS',     'Ver el registro de accesos',                'cat access.log'),
('defense', 'FIREWALL',          'Verificar las reglas activas',              'iptables -L INPUT');

commit;

-- =====================================================================================================
-- Verificación (opcional, ejecutar a mano DESPUÉS del script; están comentadas a propósito)
-- =====================================================================================================

-- a) Conteos generales: 20 niveles, 72 preguntas, 12 retos
-- select (select count(*) from public.fighter_level_curve) as niveles,
--        (select count(*) from public.battle_questions)    as preguntas,
--        (select count(*) from public.ulti_challenges)     as retos;
--    esperado: 20 | 72 | 12

-- b) Curva: nivel 1 = 0 XP, nivel 2 = 100, ..., nivel 20 = 1900 (min_xp = (nivel - 1) * 100)
-- select count(*) as filas_mal from public.fighter_level_curve where min_xp <> (level - 1) * 100;   -- esperado: 0
-- select min(level), max(level), min(min_xp), max(min_xp) from public.fighter_level_curve;           -- 1 | 20 | 0 | 1900

-- c) 12 preguntas por tema (las 6 filas deben decir 12) y 4 por escalón en cada tema (18 filas con 4)
-- select topic, count(*) as preguntas from public.battle_questions group by topic order by topic;
-- select topic, tier, count(*) as preguntas from public.battle_questions group by topic, tier order by topic, tier;

-- d) 18 respuestas correctas de cada letra en total, y 3 de cada letra en cada tema (24 filas con 3)
-- select correct, count(*) as preguntas from public.battle_questions group by correct order by correct;
-- select topic, correct, count(*) as preguntas from public.battle_questions group by topic, correct order by topic, correct;

-- e) La respuesta correcta NO está siempre en la misma letra dentro de un tema: cada tema debe usar las 4 letras
--    (las 6 filas deben decir 4)
-- select topic, count(distinct correct) as letras_distintas from public.battle_questions group by topic order by topic;

-- f) Ninguna pregunta repite opciones iguales (esperado: 0 filas)
-- select id, question from public.battle_questions
-- where option_a in (option_b, option_c, option_d) or option_b in (option_c, option_d) or option_c = option_d;

-- g) Ninguna pregunta repetida (esperado: 0 filas)
-- select question, count(*) from public.battle_questions group by question having count(*) > 1;

-- h) Todas activas y con la respuesta correcta en A a D (esperado: 0 filas)
-- select id from public.battle_questions where not active or correct not in ('A', 'B', 'C', 'D');

-- i) Retos: 6 de ataque y 6 de defensa, un reto de cada tipo en cada uno de los 6 temas (12 filas con 1)
-- select kind, count(*) as retos from public.ulti_challenges group by kind order by kind;
-- select kind, topic, count(*) as retos from public.ulti_challenges group by kind, topic order by kind, topic;

-- j) Ningún comando se repite entre retos (esperado: 0 filas) y ninguna pareja ataque/defensa comparte comando
-- select expected_command, count(*) from public.ulti_challenges group by expected_command having count(*) > 1;

-- k) Los comandos deben coincidir EXACTAMENTE con learning_labs.json (comparar a mano o con la tabla del commit):
-- select kind, topic, objective_title, expected_command from public.ulti_challenges order by kind, topic;

-- l) Las tablas siguen cerradas: ni anon ni authenticated pueden leer el banco ni los retos (esperado: 0 filas),
--    y authenticated solo lee la curva (los permisos son los de 0008, este script no los cambia)
-- select grantee, table_name, privilege_type
-- from information_schema.role_table_grants
-- where table_schema = 'public' and table_name in ('battle_questions', 'ulti_challenges')
--   and grantee in ('anon', 'authenticated');
