# RoutePH (Java + HTML/CSS/JS + XAMPP MySQL + Gmail OTP)

## Setup (once)
1. **XAMPP**: open the Control Panel and Start **MySQL**. (Apache is not needed.)
2. **MySQL driver**: download "MySQL Connector/J" (Platform Independent .zip) from
   https://dev.mysql.com/downloads/connector/j/ and copy `mysql-connector-j-x.x.x.jar` into the `lib/` folder.
3. **Gmail OTP**: Google Account -> Security -> turn on 2-Step Verification -> "App passwords" -> create one.
   Put it in `config.properties`:
       mail.user=yourname@gmail.com
       mail.appPassword=abcd efgh ijkl mnop
   (Leave the defaults to test: the OTP is then printed in the Java console instead of emailed.)
4. If your MySQL root has a password, set `db.password` in `config.properties`.

## Run
- Windows: double-click `run.bat`   |   Mac/Linux: `./run.sh`
- Open http://localhost:8080
- The database `routeph` and its tables are created automatically (or import `database.sql` in phpMyAdmin).

## NetBeans
Add the connector jar to the project Libraries, copy `src/com/mycompany/routeph1/*.java` into your package,
set `WebServer` as the main class, and run it from the project folder (where `config.properties` and `web/` are).

## Map
Leaflet + OpenStreetMap tiles + OSRM road routing: free, no API key. "Start navigation" runs inside the page
(step-by-step with live GPS dot); it never opens Google Maps.

## Screens (same as the design video)
Login / Create account (username, email, password + confirm) / Forgot password in 3 steps
(email -> 6-box OTP from Gmail -> new password) / Home (latest update banner, fixed PUP location, destinations,
routes) / Route (map, timeline, Start Navigation, comments with likes + replies) / Notifications (advisories, likes, replies).

OTP is used for: forgot password, and verifying a new account's email.
Post a service advisory: add a row to the `advisories` table in phpMyAdmin.
