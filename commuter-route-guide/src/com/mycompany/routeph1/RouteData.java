package com.mycompany.routeph1;

import java.util.*;

/**
 * Route data taken from the original ROUTEPH3 console program.
 * Every step also has map coordinates (approximate) so the web app can draw it.
 */
public class RouteData {

    public static final double[] ORIGIN = {14.5974, 121.0110}; // PUP Sta. Mesa

    static class Step {
        String text, mode; double lat, lng;
        Step(String text, String mode, double lat, double lng) {
            this.text = text; this.mode = mode; this.lat = lat; this.lng = lng;
        }
    }

    static class Route {
        int no; String title, summary; List<Step> steps = new ArrayList<>();
        Route(int no, String title, String summary) { this.no = no; this.title = title; this.summary = summary; }
        Route s(String text, String mode, double lat, double lng) { steps.add(new Step(text, mode, lat, lng)); return this; }
    }

    static final Map<String, List<Route>> DATA = new LinkedHashMap<>();

    static {
        // ===== CALOOCAN (2 routes) =====
        List<Route> cal = new ArrayList<>();
        cal.add(new Route(1, "First Route", "PUP Sta Mesa, LRT-2, LRT-1, Tricycle, South Caloocan.")
            .s("Walk/ride to LRT-2 Pureza station.", "walk", 14.6020, 121.0052)
            .s("Ride LRT-2 Pureza toward Recto Station.", "train", 14.6035, 120.9833)
            .s("Transfer to LRT-1 Doroteo Jose Station.", "walk", 14.6054, 120.9820)
            .s("Ride LRT-1 Doroteo Jose Train toward LRT-1 5th Avenue Station.", "train", 14.6443, 120.9832)
            .s("Get off at LRT-1 5th Avenue Station and take a ride tricycle C3 Caloocan.", "walk", 14.6450, 120.9836)
            .s("Tricycle to Landaska.", "ebike", 14.6530, 120.9790)
            .s("Landaska to your Destination.", "walk", 14.6600, 120.9760));
        cal.add(new Route(2, "Second Route", "PUP Sta. Mesa, LRT-2, Jeep, Tricycle, E-Bike, South Caloocan.")
            .s("Walk/ride to LRT-2 Pureza station.", "walk", 14.6020, 121.0052)
            .s("Ride LRT-2 Pureza toward Recto Station.", "train", 14.6035, 120.9833)
            .s("Exit LRT-2 Recto Station and walk toward to take a ride Jeep.", "walk", 14.6040, 120.9825)
            .s("Jeep to C3 Caloocan.", "jeep", 14.6450, 120.9740)
            .s("C3 Caloocan to Landaska (Tricycle).", "tricycle", 14.6530, 120.9790)
            .s("Landaska to your Destination (E-Bike).", "ebike", 14.6600, 120.9760));
        DATA.put("Caloocan", cal);

        // ===== BULACAN (3 routes) =====
        List<Route> bul = new ArrayList<>();
        bul.add(new Route(1, "First Route", "PUP Sta Mesa, LRT-2, LRT-1, Monumento, Bulacan.")
            .s("Walk/ride to LRT-2 Pureza station.", "walk", 14.6020, 121.0052)
            .s("Ride LRT-2 Pureza toward Recto Station.", "train", 14.6035, 120.9833)
            .s("Transfer to LRT-1 Doroteo Jose Station.", "walk", 14.6054, 120.9820)
            .s("Ride LRT-1 Doroteo Jose toward Monumento Station.", "train", 14.6543, 120.9838)
            .s("Get off at Monumento and head to the back of Victory Mall.", "walk", 14.6560, 120.9842)
            .s("Take a ride at Meycauayan Jeep Terminal.", "jeep", 14.7370, 120.9600)
            .s("After the last stop, walk toward your destination.", "walk", 14.7400, 120.9590));
        bul.add(new Route(2, "Second Route", "PUP Sta. Mesa, LRT-2, Santrans Bus Terminal, Bulacan.")
            .s("Walk/ride to LRT-2 Pureza station.", "walk", 14.6020, 121.0052)
            .s("Ride LRT-2 Pureza toward Recto Station.", "train", 14.6035, 120.9833)
            .s("Exit LRT-2 Recto Station and walk toward to BUS Santrans Terminal.", "walk", 14.6045, 120.9812)
            .s("Take a ride Bus to Duhat (Marilao Exit).", "bus", 14.7580, 120.9510)
            .s("Take a ride Tricycle Duhat to your Destination.", "tricycle", 14.7650, 120.9480));
        bul.add(new Route(3, "Third Route", "PUP Sta. Mesa, Cubao, Caloocan, Bulacan.")
            .s("Walk/ride to LRT-2 Pureza station.", "train", 14.6020, 121.0052)
            .s("Ride LRT-2 Pureza Train toward LRT-2 Cubao Station.", "train", 14.6229, 121.0527)
            .s("Exit LRT-2 Cubao Station and take a ride BUS to SM FAIRVIEW.", "bus", 14.7338, 121.0580)
            .s("Exit in SM FAIRVIEW then take a ride Jeep to Kaliwa Bagong Silang.", "jeep", 14.7570, 121.0700)
            .s("Exit in Bagong Silang last stop and take a ride tricycle to your Destination.", "tricycle", 14.7700, 121.0720));
        DATA.put("Bulacan", bul);
    }

    public static String canonical(String dest) {
        if (dest == null) return null;
        for (String k : DATA.keySet()) if (k.equalsIgnoreCase(dest.trim())) return k;
        return null;
    }

    static final String[] MODE_ORDER = {"train", "walk", "bus", "jeep", "tricycle", "ebike"};

    static List<Object> modes(Route r) {
        List<Object> out = new ArrayList<>();
        for (String m : MODE_ORDER) for (Step s : r.steps) if (s.mode.equals(m)) { out.add(m); break; }
        return out;
    }

    public static List<Object> list(String dest) {
        List<Object> out = new ArrayList<>();
        for (Route r : DATA.get(dest)) out.add(Json.obj("no", r.no, "title", r.title, "summary", r.summary, "stops", r.steps.size(), "modes", modes(r)));
        return out;
    }

    public static Map<String, Object> details(String dest, int no) {
        for (Route r : DATA.get(dest)) {
            if (r.no != no) continue;
            List<Object> steps = new ArrayList<>();
            int n = 1;
            for (Step s : r.steps)
                steps.add(Json.obj("n", n++, "text", s.text, "mode", s.mode, "lat", s.lat, "lng", s.lng));
            return Json.obj("no", r.no, "title", r.title, "summary", r.summary, "stops", r.steps.size(), "modes", modes(r), "destination", dest,
                    "origin", Json.obj("name", "PUP Sta. Mesa", "lat", ORIGIN[0], "lng", ORIGIN[1]), "steps", steps);
        }
        return null;
    }
}
