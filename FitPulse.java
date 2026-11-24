import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/* =====================================================================
 *  FitPulse - Smart Fitness Habit Coach          (Theme: Neon Heartbeat)
 *
 *  MODULES
 *   1. User Management            : Register / Login (PIN) / Profile / Switch user
 *   2. Fitness Activity Tracking  : steps, distance, calories from steps
 *   3. Workout Management         : multiple workouts per day (OOP: inheritance + polymorphism)
 *   4. Diet & Health Tracking     : meals + calorie intake, weight, sleep, water, BMI
 *   5. Goal & Progress Management : adaptive goals, Consistency Score, streak, weekly chart
 *   6. Reports & Recommendations  : weekly report (export to .txt) + personalised tips
 *
 *  Java concepts: Swing, OOP, Inheritance/Polymorphism, ArrayList,
 *                 Exception handling, File handling (no MySQL / no API)
 *  Run: javac FitPulse.java && java FitPulse
 * ===================================================================== */

final class Theme {
    static final Color BG = new Color(0x0B0F1A), CARD = new Color(0x141B2D), FIELD = new Color(0x1E2740);
    static final Color CYAN = new Color(0x00E5FF), PINK = new Color(0xFF2E93), LIME = new Color(0xB6FF3B);
    static final Color TEXT = new Color(0xE8ECF5), MUTED = new Color(0x8A94AD), BLUE = new Color(0x4D8DFF);
    static Font font(int style, int size) { return new Font(Font.SANS_SERIF, style, size); }
    static Color scoreColor(double s) { return s >= 80 ? LIME : (s >= 50 ? CYAN : PINK); }
}

class InvalidInputException extends Exception {
    InvalidInputException(String msg) { super(msg); }
}

/* ===================== MODULE 3 : WORKOUT TYPES ====================== */
abstract class Workout {
    static final String[] TYPES = {"Walking", "Running", "Cycling", "Yoga", "Strength", "Swimming"};
    private final String name; private final double met;
    Workout(String name, double met) { this.name = name; this.met = met; }
    String getName() { return name; }
    double caloriesBurned(double weightKg, int minutes) { return met * weightKg * minutes / 60.0; }
    abstract String tip();
    static Workout of(String type) {
        switch (type) {
            case "Running":  return new Running();
            case "Cycling":  return new Cycling();
            case "Yoga":     return new Yoga();
            case "Strength": return new Strength();
            case "Swimming": return new Swimming();
            default:         return new Walking();
        }
    }
}
class Walking extends Workout {
    Walking() { super("Walking", 3.5); }
    String tip() { return "Brisk walking keeps joints happy and streaks easy."; }
}
class Running extends Workout {
    Running() { super("Running", 9.8); }
    @Override double caloriesBurned(double w, int m) { return super.caloriesBurned(w, m) * 1.05; }
    String tip() { return "Warm up 5 minutes before you sprint."; }
}
class Cycling extends Workout {
    Cycling() { super("Cycling", 7.5); }
    String tip() { return "Steady cadence beats short bursts of speed."; }
}
class Yoga extends Workout {
    Yoga() { super("Yoga", 2.8); }
    String tip() { return "Great for recovery - pair it with your water goal."; }
}
class Strength extends Workout {
    Strength() { super("Strength", 5.0); }
    @Override double caloriesBurned(double w, int m) { return super.caloriesBurned(w, m) * 1.10; }
    String tip() { return "Rest 48 hours before training the same muscle again."; }
}
class Swimming extends Workout {
    Swimming() { super("Swimming", 6.0); }
    String tip() { return "Full-body, low-impact - great on sore-joint days."; }
}

/* ======================== DATA MODEL (ArrayList) ===================== */
class User {
    String username, name, gender; int pinHash, age; double height, weight;
    User(String username, int pinHash, String name, int age, String gender, double height, double weight) {
        this.username = username; this.pinHash = pinHash; this.name = name; this.age = age;
        this.gender = gender; this.height = height; this.weight = weight;
    }
    static int hash(String pin) { return ("fp#" + pin).hashCode(); }
    double bmi(double w) { double m = height / 100.0; return w / (m * m); }
    String bmiCategory(double w) {
        double b = bmi(w);
        return b < 18.5 ? "Underweight" : b < 25 ? "Normal" : b < 30 ? "Overweight" : "Obese";
    }
}

class Goals {
    final int steps, water, workout;
    Goals(int steps, int water, int workout) { this.steps = steps; this.water = water; this.workout = workout; }
}

class ActivityEntry {
    final LocalDate date; final int steps; final double distance;
    ActivityEntry(LocalDate d, int s, double dist) { date = d; steps = s; distance = dist; }
}
class WorkoutEntry {
    final LocalDate date; final String type; final int minutes; final double calories;
    WorkoutEntry(LocalDate d, String t, int m, double c) { date = d; type = t; minutes = m; calories = c; }
}
class MealEntry {
    final LocalDate date; final String meal, food; final int calories;
    MealEntry(LocalDate d, String m, String f, int c) { date = d; meal = m; food = f; calories = c; }
}
class HealthEntry {
    final LocalDate date; final double weight, sleep; final int water;   // 0 = not recorded
    HealthEntry(LocalDate d, double w, double s, int wa) { date = d; weight = w; sleep = s; water = wa; }
}

class DaySummary {
    LocalDate date; int steps, water, workoutMin, intake, calTarget;
    double distance, burned, weight, sleep; Goals goals; boolean any;
    private static double r(int a, int g) { return g <= 0 ? 1.0 : Math.min(1.0, (double) a / g); }
    double stepRatio()    { return r(steps, goals.steps); }
    double waterRatio()   { return r(water, goals.water); }
    double workoutRatio() { return r(workoutMin, goals.workout); }
    double dietRatio() {
        if (intake <= 0 || calTarget <= 0) return 0;
        double dev = Math.abs(intake - calTarget) / (double) calTarget;
        return dev <= 0.15 ? 1.0 : Math.max(0, 1 - (dev - 0.15) / 0.45);
    }
    /** Consistency Score = steps 30% + workout 30% + water 20% + diet 20% */
    double score() { return 100.0 * (0.30 * stepRatio() + 0.30 * workoutRatio() + 0.20 * waterRatio() + 0.20 * dietRatio()); }
    boolean completed() { return any && score() >= 80; }
}

class UserData {
    User user;
    Goals baseGoals = new Goals(6000, 8, 30);
    ArrayList<ActivityEntry> activities = new ArrayList<>();
    ArrayList<WorkoutEntry> workouts = new ArrayList<>();
    ArrayList<MealEntry> meals = new ArrayList<>();
    ArrayList<HealthEntry> health = new ArrayList<>();
    TreeMap<LocalDate, Goals> goalLog = new TreeMap<>();   // goal snapshot per day

    UserData(User u) { user = u; }

    static int roundTo(double v, int step) { return (int) (Math.round(v / step) * step); }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    double currentWeight(LocalDate upto) {
        HealthEntry best = null;
        for (HealthEntry h : health)
            if (h.weight > 0 && !h.date.isAfter(upto) && (best == null || h.date.isAfter(best.date))) best = h;
        return best == null ? user.weight : best.weight;
    }

    int calorieTarget(LocalDate d) {   // Mifflin-St Jeor x 1.4 activity factor
        double bmr = 10 * currentWeight(d) + 6.25 * user.height - 5 * user.age + (user.gender.equals("Male") ? 5 : -161);
        return (int) (Math.round(bmr * 1.4 / 50.0) * 50);
    }

    DaySummary raw(LocalDate d) {
        DaySummary s = new DaySummary(); s.date = d;
        double w = currentWeight(d);
        for (ActivityEntry a : activities) if (a.date.equals(d)) { s.steps += a.steps; s.distance += a.distance; s.any = true; }
        s.burned += s.steps * 0.04 * (w / 70.0);
        for (WorkoutEntry e : workouts) if (e.date.equals(d)) { s.workoutMin += e.minutes; s.burned += e.calories; s.any = true; }
        for (MealEntry m : meals) if (m.date.equals(d)) { s.intake += m.calories; s.any = true; }
        for (HealthEntry h : health) if (h.date.equals(d)) { s.water = h.water; s.weight = h.weight; s.sleep = h.sleep; s.any = true; }
        return s;
    }
    DaySummary summary(LocalDate d) { DaySummary s = raw(d); s.goals = goalsOn(d); s.calTarget = calorieTarget(d); return s; }

    /** Adaptive goals: built from the previous 7 days of real activity. */
    Goals goalsOn(LocalDate d) {
        Goals snap = goalLog.get(d);
        if (snap != null) return snap;
        Map.Entry<LocalDate, Goals> e = goalLog.lowerEntry(d);
        if (e == null) return baseGoals;
        Goals prev = e.getValue();
        List<DaySummary> window = new ArrayList<>();
        for (int i = 1; i <= 7; i++) { DaySummary s = raw(d.minusDays(i)); if (s.any) window.add(s); }
        if (window.isEmpty()) return prev;
        double avgS = window.stream().mapToInt(x -> x.steps).average().orElse(0);
        double avgW = window.stream().mapToInt(x -> x.water).average().orElse(0);
        double avgM = window.stream().mapToInt(x -> x.workoutMin).average().orElse(0);
        int steps = avgS >= prev.steps ? roundTo(prev.steps * 1.10, 100) : roundTo(Math.min(prev.steps, avgS * 1.12), 100);
        int water = avgW >= prev.water ? prev.water + 1 : (int) Math.min(prev.water, Math.ceil(avgW + 1));
        int work = avgM >= prev.workout ? roundTo(Math.max(prev.workout * 1.10, prev.workout + 5), 5)
                                        : roundTo(Math.min(prev.workout, avgM * 1.15), 5);
        return new Goals(clamp(steps, 2000, 20000), clamp(water, 4, 12), clamp(work, 10, 120));
    }
    void snapshot(LocalDate d) { if (!goalLog.containsKey(d)) goalLog.put(d, goalsOn(d)); }

    int streak(LocalDate today) {
        DaySummary t = summary(today);
        if (t.any && !t.completed()) return 0;
        LocalDate d = t.any ? today : today.minusDays(1);
        int s = 0;
        while (summary(d).completed()) { s++; d = d.minusDays(1); }
        return s;
    }
    double weekAvg(LocalDate end) {
        double sum = 0;
        for (int i = 0; i < 7; i++) { DaySummary s = summary(end.minusDays(i)); if (s.any) sum += s.score(); }
        return sum / 7.0;
    }
    static String rank(double avg) {
        return avg < 30 ? "SPARK" : avg < 55 ? "EMBER" : avg < 75 ? "FLAME" : avg < 90 ? "BLAZE" : "INFERNO";
    }
    LocalDate latestDate() {
        LocalDate m = null;
        for (ActivityEntry a : activities) m = max(m, a.date);
        for (WorkoutEntry a : workouts) m = max(m, a.date);
        for (MealEntry a : meals) m = max(m, a.date);
        for (HealthEntry a : health) m = max(m, a.date);
        return m;
    }
    private static LocalDate max(LocalDate a, LocalDate b) { return a == null || b.isAfter(a) ? b : a; }
    void clearRecords() { activities.clear(); workouts.clear(); meals.clear(); health.clear(); goalLog.clear(); }
}

/* ===================== FILE HANDLING (persistence) =================== */
class FileStore {
    static final String USERS = "fitpulse_users.txt";
    static String dataFile(String u) { return "fitpulse_data_" + u + ".txt"; }
    static String clean(String s) { return s.replace("|", "/").replace("\n", " "); }

    static ArrayList<User> loadUsers() {
        ArrayList<User> list = new ArrayList<>();
        File f = new File(USERS);
        if (!f.exists()) return list;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                try {
                    if (p[0].equals("USER"))
                        list.add(new User(p[1], Integer.parseInt(p[2]), p[3], Integer.parseInt(p[4]), p[5],
                                Double.parseDouble(p[6]), Double.parseDouble(p[7])));
                } catch (RuntimeException bad) { /* skip corrupted line */ }
            }
        } catch (IOException e) { /* treat as empty */ }
        return list;
    }
    static void saveUsers(List<User> list) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(USERS)))) {
            for (User u : list)
                pw.println("USER|" + u.username + "|" + u.pinHash + "|" + clean(u.name) + "|" + u.age + "|" + u.gender + "|" + u.height + "|" + u.weight);
        }
    }
    static User find(String username) {
        for (User u : loadUsers()) if (u.username.equalsIgnoreCase(username)) return u;
        return null;
    }
    static void addUser(User u) throws IOException { ArrayList<User> l = loadUsers(); l.add(u); saveUsers(l); }
    static void updateUser(User u) throws IOException {
        ArrayList<User> l = loadUsers();
        for (int i = 0; i < l.size(); i++) if (l.get(i).username.equals(u.username)) l.set(i, u);
        saveUsers(l);
    }
    static void deleteUser(String username) throws IOException {
        ArrayList<User> l = loadUsers();
        l.removeIf(x -> x.username.equals(username));
        saveUsers(l);
        new File(dataFile(username)).delete();
    }

    static void saveData(UserData d) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(dataFile(d.user.username))))) {
            pw.println("GOALS|" + d.baseGoals.steps + "|" + d.baseGoals.water + "|" + d.baseGoals.workout);
            for (Map.Entry<LocalDate, Goals> e : d.goalLog.entrySet())
                pw.println("SNAP|" + e.getKey() + "|" + e.getValue().steps + "|" + e.getValue().water + "|" + e.getValue().workout);
            for (ActivityEntry a : d.activities) pw.println("ACT|" + a.date + "|" + a.steps + "|" + String.format(Locale.US, "%.2f", a.distance));
            for (WorkoutEntry w : d.workouts) pw.println("WRK|" + w.date + "|" + w.type + "|" + w.minutes + "|" + String.format(Locale.US, "%.1f", w.calories));
            for (MealEntry m : d.meals) pw.println("MEAL|" + m.date + "|" + m.meal + "|" + clean(m.food) + "|" + m.calories);
            for (HealthEntry h : d.health) pw.println("HLT|" + h.date + "|" + h.weight + "|" + h.sleep + "|" + h.water);
        }
    }
    static UserData loadData(User u) {
        UserData d = new UserData(u);
        File f = new File(dataFile(u.username));
        if (!f.exists()) return d;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                try {
                    switch (p[0]) {
                        case "GOALS": d.baseGoals = new Goals(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])); break;
                        case "SNAP":  d.goalLog.put(LocalDate.parse(p[1]), new Goals(Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]))); break;
                        case "ACT":   d.activities.add(new ActivityEntry(LocalDate.parse(p[1]), Integer.parseInt(p[2]), Double.parseDouble(p[3]))); break;
                        case "WRK":   d.workouts.add(new WorkoutEntry(LocalDate.parse(p[1]), p[2], Integer.parseInt(p[3]), Double.parseDouble(p[4]))); break;
                        case "MEAL":  d.meals.add(new MealEntry(LocalDate.parse(p[1]), p[2], p[3], Integer.parseInt(p[4]))); break;
                        case "HLT":   d.health.add(new HealthEntry(LocalDate.parse(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Integer.parseInt(p[4]))); break;
                        default: break;
                    }
                } catch (RuntimeException bad) { /* skip corrupted line */ }
            }
        } catch (IOException e) { /* return what we have */ }
        return d;
    }
}

/* ================= MODULE 6 : COACH + REPORTS ENGINE ================= */
class Coach {
    static String fmt(int n) { return String.format("%,d", n); }

    static String suggestion(UserData ud, DaySummary s) {
        Goals n = ud.goalsOn(s.date.plusDays(1));
        int nt = ud.calorieTarget(s.date.plusDays(1));
        StringBuilder sb = new StringBuilder(); boolean missed = false;
        if (s.steps < s.goals.steps) { missed = true;
            sb.append("Today: ").append(fmt(s.steps)).append(" steps. Tomorrow, try ").append(fmt(n.steps)).append(" steps.\n"); }
        if (s.water < s.goals.water) { missed = true;
            sb.append("Today: ").append(s.water).append(" glasses of water. Tomorrow, try ").append(n.water).append(" glasses.\n"); }
        if (s.workoutMin < s.goals.workout) { missed = true;
            sb.append("Today: ").append(s.workoutMin).append(" min of workout. Tomorrow, try ").append(n.workout).append(" min.\n"); }
        if (s.intake == 0) { missed = true;
            sb.append("No meals logged today. Tomorrow, log all your meals.\n");
        } else if (s.dietRatio() < 1) { missed = true;
            if (s.intake > s.calTarget) sb.append("Today: ").append(fmt(s.intake)).append(" kcal (target ").append(fmt(s.calTarget)).append("). Tomorrow, stay within ").append(fmt(nt)).append(" kcal.\n");
            else sb.append("Today: only ").append(fmt(s.intake)).append(" kcal. Tomorrow, eat close to ").append(fmt(nt)).append(" kcal.\n");
        }
        if (!missed)
            sb.append("Great job! All goals completed today. Level-up for tomorrow: ").append(fmt(n.steps)).append(" steps, ")
              .append(n.water).append(" glasses, ").append(n.workout).append(" min workout, ~").append(fmt(nt)).append(" kcal.\n");
        return sb.toString();
    }

    static String challenge(UserData ud, LocalDate today) {
        DaySummary t = ud.summary(today), ref = null;
        for (int i = 1; i <= 30 && ref == null; i++) { DaySummary x = ud.summary(today.minusDays(i)); if (x.any) ref = x; }
        if (ref == null) return "First Pulse Challenge: log your first day to start your streak.";
        double[] r = {ref.stepRatio(), ref.waterRatio(), ref.workoutRatio(), ref.dietRatio()};
        int k = 0; for (int i = 1; i < 4; i++) if (r[i] < r[k]) k = i;
        String text; boolean done;
        switch (k) {
            case 0:  text = "Step Sprint - walk " + fmt(t.goals.steps) + " steps today."; done = t.steps >= t.goals.steps; break;
            case 1:  text = "Hydration Hero - drink " + t.goals.water + " glasses, one every 2 hours."; done = t.water >= t.goals.water; break;
            case 2:  text = "Power Block - complete " + t.goals.workout + " minutes of workout."; done = t.workoutMin >= t.goals.workout; break;
            default: text = "Balanced Plate - keep intake near " + fmt(t.calTarget) + " kcal."; done = t.dietRatio() >= 1; break;
        }
        return text + (done ? "   [COMPLETED]" : "   [PENDING]");
    }

    static List<String> recommendations(UserData ud, LocalDate end) {
        List<String> out = new ArrayList<>();
        List<DaySummary> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) { DaySummary s = ud.summary(end.minusDays(i)); if (s.any) days.add(s); }
        User u = ud.user; double w = ud.currentWeight(end);
        if (days.isEmpty()) { out.add("Log some activity, workouts and meals to unlock personalised recommendations."); return out; }

        String cat = u.bmiCategory(w);
        if (cat.equals("Normal")) out.add(String.format("BMI %.1f is in the healthy range - focus on maintaining your habits.", u.bmi(w)));
        else if (cat.equals("Underweight")) out.add(String.format("BMI %.1f is below the healthy range - add nutritious calorie-dense foods and strength training.", u.bmi(w)));
        else out.add(String.format("BMI %.1f is above the healthy range - aim for gentle calorie control and regular activity (consult a doctor for a plan).", u.bmi(w)));

        double avgSteps = days.stream().mapToInt(x -> x.steps).average().orElse(0);
        Goals tom = ud.goalsOn(end.plusDays(1));
        if (avgSteps < tom.steps) out.add("Average steps are " + fmt((int) avgSteps) + "/day. Try a 10-minute walk after each meal; tomorrow's target is " + fmt(tom.steps) + ".");
        else out.add("Great step average (" + fmt((int) avgSteps) + "/day) - the coach is raising your target to " + fmt(tom.steps) + ".");

        int totalMin = days.stream().mapToInt(x -> x.workoutMin).sum();
        if (totalMin < 150) out.add("You did " + totalMin + " workout minutes this week; the common guideline is 150 min of moderate activity.");
        java.util.Set<String> types = new java.util.HashSet<>();
        for (WorkoutEntry e : ud.workouts) if (!e.date.isAfter(end) && !e.date.isBefore(end.minusDays(6))) types.add(e.type);
        if (types.size() == 1) out.add("You only did " + types.iterator().next() + " - mix cardio with strength or yoga for balance.");

        double avgWater = days.stream().mapToInt(x -> x.water).average().orElse(0);
        if (avgWater < tom.water) out.add(String.format("Water average is %.1f glasses/day - keep a bottle at your desk.", avgWater));

        List<DaySummary> slept = new ArrayList<>();
        for (DaySummary d : days) if (d.sleep > 0) slept.add(d);
        if (!slept.isEmpty()) {
            double avgSleep = slept.stream().mapToDouble(x -> x.sleep).average().orElse(0);
            if (avgSleep < 7) out.add(String.format("Sleep average is %.1f h - aim for 7-9 hours for better recovery.", avgSleep));
        }
        List<DaySummary> ate = new ArrayList<>();
        for (DaySummary d : days) if (d.intake > 0) ate.add(d);
        if (!ate.isEmpty()) {
            double avgIn = ate.stream().mapToInt(x -> x.intake).average().orElse(0);
            int target = ud.calorieTarget(end);
            if (avgIn > target * 1.15) out.add("Average intake (" + fmt((int) avgIn) + " kcal) is above your ~" + fmt(target) + " kcal target - trim snacks and sugary drinks.");
            else if (avgIn < target * 0.85) out.add("Average intake (" + fmt((int) avgIn) + " kcal) is well below your ~" + fmt(target) + " kcal target - eat enough to fuel workouts.");
            else out.add("Calorie intake is on target - nice balance.");
        } else out.add("No meals logged this week - log your meals so the coach can check your diet.");

        int st = ud.streak(end);
        out.add(st >= 3 ? "You are on a " + st + "-day streak - protect it!" : "Hit a score of 80+ for 3 days in a row to start a streak.");
        return out;
    }

    static String weeklyReport(UserData ud, LocalDate end) {
        User u = ud.user; LocalDate start = end.minusDays(6);
        StringBuilder sb = new StringBuilder();
        sb.append("FITPULSE WEEKLY REPORT\n======================\n");
        sb.append("User   : ").append(u.name).append(" (").append(u.username).append(")\n");
        sb.append("Period : ").append(start).append(" to ").append(end).append("\n");
        double w = ud.currentWeight(end);
        sb.append(String.format("Weight : %.1f kg   BMI : %.1f (%s)%n%n", w, u.bmi(w), u.bmiCategory(w)));
        sb.append(String.format("%-11s %-6s %-7s %-6s %-8s %-7s %-7s%n", "Date", "Score", "Steps", "Water", "Workout", "Intake", "Burned"));
        sb.append("---------------------------------------------------------\n");
        int steps = 0, mins = 0, done = 0, n = 0; double burned = 0, scoreSum = 0, bestScore = -1, firstW = 0, lastW = 0; LocalDate best = null;
        long intakeSum = 0; int intakeDays = 0;
        for (int i = 6; i >= 0; i--) {
            DaySummary s = ud.summary(end.minusDays(i));
            if (!s.any) { sb.append(String.format("%-11s -- no data --%n", s.date)); continue; }
            n++; steps += s.steps; mins += s.workoutMin; burned += s.burned; scoreSum += s.score();
            if (s.completed()) done++;
            if (s.score() > bestScore) { bestScore = s.score(); best = s.date; }
            if (s.intake > 0) { intakeSum += s.intake; intakeDays++; }
            if (s.weight > 0) { if (firstW == 0) firstW = s.weight; lastW = s.weight; }
            sb.append(String.format("%-11s %-6.0f %-7d %-6d %-8d %-7d %-7.0f%s%n", s.date, s.score(), s.steps, s.water, s.workoutMin, s.intake, s.burned, s.completed() ? " *" : ""));
        }
        sb.append("\n* = streak day (score >= 80)\n\nSUMMARY\n-------\n");
        sb.append(String.format("Average score   : %.1f   (rank %s)%n", n == 0 ? 0 : scoreSum / n, UserData.rank(ud.weekAvg(end))));
        sb.append("Streak days     : ").append(done).append(" / 7   (current streak ").append(ud.streak(end)).append(")\n");
        sb.append("Total steps     : ").append(fmt(steps)).append("\nWorkout minutes : ").append(mins).append("\n");
        sb.append(String.format("Calories burned : %.0f kcal%n", burned));
        if (intakeDays > 0) sb.append(String.format("Avg intake      : %d kcal/day (target ~%d)%n", intakeSum / intakeDays, ud.calorieTarget(end)));
        if (firstW > 0 && lastW > 0) sb.append(String.format("Weight change   : %+.1f kg%n", lastW - firstW));
        if (best != null) sb.append(String.format("Best day        : %s (%.0f)%n", best, bestScore));
        sb.append("\nRECOMMENDATIONS\n---------------\n");
        int i = 1;
        for (String r : recommendations(ud, end)) sb.append(i++).append(". ").append(r).append("\n");
        return sb.toString();
    }
}

/* ====================== DEMO DATA (for presentation) ================= */
class Demo {
    static void fill(UserData ud) {
        ud.clearRecords();
        Random rnd = new Random(7);
        LocalDate today = LocalDate.now();
        String[][] foods = {{"Idli & sambar", "Dosa & chutney", "Oats & banana", "Poha"},
                {"Rice, dal & veg", "Curd rice & poriyal", "Chapati & curry", "Vegetable biryani"},
                {"Fruit bowl", "Sundal", "Roasted nuts", "Buttermilk & biscuits"},
                {"Chapati & paneer", "Upma", "Ragi dosa", "Veg soup & salad"}};
        String[] meal = {"Breakfast", "Lunch", "Snack", "Dinner"};
        int[][] range = {{300, 200}, {450, 300}, {120, 180}, {400, 300}};
        double weight = ud.user.weight + 0.8;
        for (int i = 14; i >= 1; i--) {
            LocalDate d = today.minusDays(i);
            double pr = (14 - i) / 13.0;
            ud.snapshot(d);
            int steps = (int) (3500 + pr * 4500 + rnd.nextInt(1800));
            ud.activities.add(new ActivityEntry(d, steps, steps * 0.00075));
            if (rnd.nextInt(5) != 0) {
                String t = Workout.TYPES[rnd.nextInt(Workout.TYPES.length)];
                int mins = 15 + (int) (pr * 20) + rnd.nextInt(15);
                ud.workouts.add(new WorkoutEntry(d, t, mins, Workout.of(t).caloriesBurned(ud.currentWeight(d), mins)));
            }
            for (int m = 0; m < 4; m++)
                ud.meals.add(new MealEntry(d, meal[m], foods[m][rnd.nextInt(4)], range[m][0] + rnd.nextInt(range[m][1])));
            weight -= 0.06;
            ud.health.add(new HealthEntry(d, Math.round(weight * 10) / 10.0, 5.5 + rnd.nextInt(30) / 10.0, 3 + (int) (pr * 4) + rnd.nextInt(3)));
        }
    }
}

/* ============================ CUSTOM PANELS ========================== */
class RingPanel extends JPanel {
    private double score = 0; private String caption = "", rank = "SPARK"; private float phase = 0;
    RingPanel() {
        setOpaque(false); setPreferredSize(new Dimension(320, 330));
        new Timer(40, e -> { phase += 0.12f; repaint(); }).start();
    }
    void update(double score, String caption, String rank) { this.score = score; this.caption = caption; this.rank = rank; repaint(); }
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight(), size = Math.min(w - 50, h - 90), x = (w - size) / 2, y = 20;
        double pulse = (Math.sin(phase) + 1) / 2;
        Color c = Theme.scoreColor(score);
        int core = (int) (size * (0.50 + 0.04 * pulse));
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (18 + 22 * pulse)));
        g.fillOval(x + (size - core) / 2, y + (size - core) / 2, core, core);
        g.setStroke(new BasicStroke(16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 25));
        g.drawOval(x, y, size, size);
        double extent = -360.0 * Math.min(score, 100) / 100.0;
        for (int i = 4; i >= 1; i--) {
            g.setStroke(new BasicStroke(16 + i * 5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (6 + 8 * pulse)));
            g.draw(new Arc2D.Double(x, y, size, size, 90, extent, Arc2D.OPEN));
        }
        g.setStroke(new BasicStroke(16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c);
        g.draw(new Arc2D.Double(x, y, size, size, 90, extent, Arc2D.OPEN));
        g.setColor(Theme.TEXT); g.setFont(Theme.font(Font.BOLD, size / 3));
        String s = String.valueOf((int) Math.round(score)); FontMetrics fm = g.getFontMetrics();
        g.drawString(s, x + (size - fm.stringWidth(s)) / 2, y + size / 2 + fm.getAscent() / 3);
        g.setFont(Theme.font(Font.BOLD, 12)); g.setColor(Theme.MUTED); fm = g.getFontMetrics();
        String t = "CONSISTENCY SCORE";
        g.drawString(t, x + (size - fm.stringWidth(t)) / 2, y + size / 2 + size / 4);
        g.setFont(Theme.font(Font.BOLD, 20)); g.setColor(c); fm = g.getFontMetrics();
        String r = "RANK: " + rank;
        g.drawString(r, (w - fm.stringWidth(r)) / 2, y + size + 32);
        g.setFont(Theme.font(Font.PLAIN, 12)); g.setColor(Theme.MUTED); fm = g.getFontMetrics();
        g.drawString(caption, (w - fm.stringWidth(caption)) / 2, y + size + 52);
        g.dispose();
    }
}

class ChartPanel extends JPanel {
    private double[] scores = new double[7]; private String[] labels = new String[7];
    ChartPanel() { setBackground(Theme.CARD); setPreferredSize(new Dimension(500, 260)); }
    void setData(double[] s, String[] l) { scores = s; labels = l; repaint(); }
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight(), left = 40, right = 16, top = 34, bottom = 36;
        int cw = w - left - right, ch = h - top - bottom;
        g.setFont(Theme.font(Font.BOLD, 14)); g.setColor(Theme.TEXT);
        g.drawString("Last 7 Days - Consistency Score", left, 22);
        g.setFont(Theme.font(Font.PLAIN, 11));
        for (int i = 0; i <= 4; i++) {
            int y = top + ch - (int) (ch * i * 25 / 100.0);
            g.setColor(new Color(255, 255, 255, 30)); g.setStroke(new BasicStroke(1)); g.drawLine(left, y, left + cw, y);
            g.setColor(Theme.MUTED); g.drawString("" + (i * 25), 8, y + 4);
        }
        int y80 = top + ch - (int) (ch * 0.80);
        g.setColor(new Color(0xB6, 0xFF, 0x3B, 120));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 6}, 0));
        g.drawLine(left, y80, left + cw, y80);
        g.drawString("streak line (80)", left + cw - 80, y80 - 4);
        int slot = cw / 7, bw = slot * 55 / 100;
        for (int i = 0; i < 7; i++) {
            int x = left + i * slot + (slot - bw) / 2; double s = scores[i];
            g.setColor(Theme.MUTED); g.setFont(Theme.font(Font.PLAIN, 12));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], x + (bw - fm.stringWidth(labels[i])) / 2, top + ch + 20);
            if (s < 0) { g.drawString("-", x + bw / 2 - 2, top + ch - 6); continue; }
            int bh = (int) (ch * s / 100.0); Color c = Theme.scoreColor(s);
            g.setPaint(new GradientPaint(0, top + ch - bh, c, 0, top + ch, new Color(c.getRed(), c.getGreen(), c.getBlue(), 60)));
            g.fillRoundRect(x, top + ch - bh, bw, Math.max(bh, 3), 10, 10);
            g.setColor(Theme.TEXT); g.setFont(Theme.font(Font.BOLD, 12)); fm = g.getFontMetrics();
            String v = String.valueOf((int) Math.round(s));
            g.drawString(v, x + (bw - fm.stringWidth(v)) / 2, top + ch - bh - 6);
        }
        g.dispose();
    }
}

/* ============== UI HELPERS (shared by Login + Main window) =========== */
class UI {
    static JLabel label(String t, int size, int style, Color c) { JLabel l = new JLabel(t); l.setFont(Theme.font(style, size)); l.setForeground(c); return l; }
    static JPanel card() {
        JPanel p = new JPanel(); p.setBackground(Theme.CARD);
        p.setBorder(new CompoundBorder(new LineBorder(new Color(0x232C45), 1, true), new EmptyBorder(12, 14, 12, 14)));
        return p;
    }
    static JTextField field(int cols) {
        JTextField f = new JTextField(cols);
        f.setBackground(Theme.FIELD); f.setForeground(Theme.TEXT); f.setCaretColor(Theme.CYAN); f.setFont(Theme.font(Font.PLAIN, 14));
        f.setBorder(new CompoundBorder(new LineBorder(new Color(0x2C3A5E)), new EmptyBorder(4, 8, 4, 8)));
        return f;
    }
    static JPasswordField pin(int cols) {
        JPasswordField f = new JPasswordField(cols);
        f.setBackground(Theme.FIELD); f.setForeground(Theme.TEXT); f.setCaretColor(Theme.CYAN); f.setFont(Theme.font(Font.PLAIN, 14));
        f.setBorder(new CompoundBorder(new LineBorder(new Color(0x2C3A5E)), new EmptyBorder(4, 8, 4, 8)));
        return f;
    }
    static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> c = new JComboBox<>(items);
        c.setBackground(Theme.FIELD); c.setForeground(Theme.TEXT); c.setFont(Theme.font(Font.PLAIN, 14));
        return c;
    }
    static JButton button(String t, Color c) {
        JButton b = new JButton(t);
        b.setBackground(c); b.setForeground(c.equals(Theme.FIELD) ? Theme.TEXT : Color.BLACK); b.setFont(Theme.font(Font.BOLD, 13));
        b.setFocusPainted(false); b.setBorder(new EmptyBorder(8, 14, 8, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    static JTextArea area(int rows) {
        JTextArea a = new JTextArea(rows, 30);
        a.setEditable(false); a.setLineWrap(true); a.setWrapStyleWord(true);
        a.setBackground(Theme.CARD); a.setForeground(Theme.TEXT); a.setFont(Theme.font(Font.PLAIN, 14));
        return a;
    }
    static JProgressBar bar(Color c) {
        JProgressBar b = new JProgressBar(0, 100);
        b.setStringPainted(true); b.setForeground(c); b.setBackground(Theme.FIELD);
        b.setBorder(BorderFactory.createEmptyBorder()); b.setFont(Theme.font(Font.BOLD, 12));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24)); b.setPreferredSize(new Dimension(300, 24));
        return b;
    }
    static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder()); sp.getViewport().setBackground(Theme.CARD);
        sp.setBackground(Theme.CARD);
        return sp;
    }
    static DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }
    static JTable table(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setBackground(Theme.CARD); t.setForeground(Theme.TEXT); t.setGridColor(new Color(0x232C45));
        t.setRowHeight(24); t.setFont(Theme.font(Font.PLAIN, 13)); t.setFillsViewportHeight(true);
        t.setSelectionBackground(new Color(0x24527A)); t.setSelectionForeground(Theme.TEXT);
        JTableHeader h = t.getTableHeader();
        h.setBackground(Theme.FIELD); h.setForeground(Theme.CYAN); h.setFont(Theme.font(Font.BOLD, 13));
        return t;
    }
    static JPanel row() { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); p.setOpaque(false); return p; }
    static JPanel section(String title, JComponent form, JComponent body) {
        JPanel c = card(); c.setLayout(new BorderLayout(0, 8));
        JPanel north = new JPanel(); north.setOpaque(false); north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        JLabel t = label(title, 13, Font.BOLD, Theme.MUTED); t.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(t);
        if (form != null) { form.setAlignmentX(Component.LEFT_ALIGNMENT); north.add(form); }
        c.add(north, BorderLayout.NORTH);
        c.add(body, BorderLayout.CENTER);
        return c;
    }
    static int parseInt(String s, String field, int lo, int hi) throws InvalidInputException {
        try {
            int v = Integer.parseInt(s.trim());
            if (v < lo || v > hi) throw new InvalidInputException(field + " must be between " + lo + " and " + hi + ".");
            return v;
        } catch (NumberFormatException e) { throw new InvalidInputException(field + " must be a whole number."); }
    }
    static double parseDouble(String s, String field, double lo, double hi) throws InvalidInputException {
        try {
            double v = Double.parseDouble(s.trim());
            if (v < lo || v > hi) throw new InvalidInputException(field + " must be between " + lo + " and " + hi + ".");
            return v;
        } catch (NumberFormatException e) { throw new InvalidInputException(field + " must be a number."); }
    }
    static LocalDate date(JTextField f) throws InvalidInputException {
        try {
            LocalDate d = LocalDate.parse(f.getText().trim());
            if (d.isAfter(LocalDate.now())) throw new InvalidInputException("Future dates are not allowed.");
            return d;
        } catch (DateTimeParseException e) { throw new InvalidInputException("Date must look like 2026-10-18."); }
    }
    static JTextField dateField() { JTextField f = field(9); f.setText(LocalDate.now().toString()); return f; }
}

/* ======================= MODULE 1 : LOGIN / REGISTER ================= */
class LoginDialog extends JDialog {
    UserData result;
    private final JTextField userF = UI.field(16);
    private final JPasswordField pinF = UI.pin(16);

    LoginDialog() {
        super((Frame) null, "FitPulse - Login", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(); root.setBackground(Theme.BG);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS)); root.setBorder(new EmptyBorder(24, 36, 24, 36));
        JLabel title = UI.label("FitPulse", 38, Font.BOLD, Theme.CYAN);
        JLabel sub = UI.label("Smart Fitness Habit Coach", 14, Font.PLAIN, Theme.MUTED);
        title.setAlignmentX(CENTER_ALIGNMENT); sub.setAlignmentX(CENTER_ALIGNMENT);
        root.add(title); root.add(sub); root.add(Box.createVerticalStrut(20));

        JPanel form = UI.card(); form.setLayout(new GridLayout(4, 1, 0, 6));
        form.add(UI.label("Username", 13, Font.BOLD, Theme.TEXT)); form.add(userF);
        form.add(UI.label("4-digit PIN", 13, Font.BOLD, Theme.TEXT)); form.add(pinF);
        form.setAlignmentX(CENTER_ALIGNMENT);
        root.add(form); root.add(Box.createVerticalStrut(16));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0)); btns.setOpaque(false);
        JButton login = UI.button("Login", Theme.CYAN), reg = UI.button("Create Account", Theme.LIME);
        login.addActionListener(e -> doLogin()); reg.addActionListener(e -> doRegister());
        btns.add(login); btns.add(reg);
        root.add(btns);
        getRootPane().setDefaultButton(login);
        setContentPane(root); pack(); setLocationRelativeTo(null); setResizable(false);
    }

    private void doLogin() {
        User u = FileStore.find(userF.getText().trim());
        if (u == null || u.pinHash != User.hash(new String(pinF.getPassword()))) {
            JOptionPane.showMessageDialog(this, "Wrong username or PIN.", "Login failed", JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = FileStore.loadData(u);
        dispose();
    }

    private void doRegister() {
        JTextField name = UI.field(14), user = UI.field(14), age = UI.field(14), h = UI.field(14), w = UI.field(14);
        JPasswordField pin = UI.pin(14);
        JComboBox<String> gender = UI.combo(new String[]{"Female", "Male"});
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8)); p.setBackground(Theme.BG);
        String[] names = {"Full name", "Username (letters/digits)", "4-digit PIN", "Age", "Gender", "Height (cm)", "Weight (kg)"};
        JComponent[] comps = {name, user, pin, age, gender, h, w};
        for (int i = 0; i < names.length; i++) { p.add(UI.label(names[i], 13, Font.PLAIN, Theme.TEXT)); p.add(comps[i]); }
        while (true) {
            if (JOptionPane.showConfirmDialog(this, p, "Create Account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            try {
                String un = user.getText().trim(), nm = name.getText().trim(), pn = new String(pin.getPassword());
                if (nm.isEmpty()) throw new InvalidInputException("Name cannot be empty.");
                if (!un.matches("[A-Za-z0-9_]{3,15}")) throw new InvalidInputException("Username: 3-15 letters, digits or _ .");
                if (!pn.matches("\\d{4}")) throw new InvalidInputException("PIN must be exactly 4 digits.");
                if (FileStore.find(un) != null) throw new InvalidInputException("That username is already taken.");
                User u = new User(un, User.hash(pn), nm, UI.parseInt(age.getText(), "Age", 5, 120), (String) gender.getSelectedItem(),
                        UI.parseDouble(h.getText(), "Height", 80, 250), UI.parseDouble(w.getText(), "Weight", 20, 300));
                FileStore.addUser(u);
                result = new UserData(u);
                FileStore.saveData(result);
                dispose();
                return;
            } catch (InvalidInputException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Please check your input", JOptionPane.WARNING_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage(), "File error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
    }
}

/* =========================== MAIN WINDOW ============================= */
public class FitPulse extends JFrame {
    static final String[] NAV = {"Home", "User Management", "Activity", "Workouts", "Diet & Health", "Goals & Progress", "Reports"};

    final UserData data;
    final CardLayout cards = new CardLayout();
    final JPanel content = new JPanel(cards);
    final JLabel status = UI.label(" ", 12, Font.PLAIN, Theme.MUTED);

    // Home
    final RingPanel ring = new RingPanel();
    JProgressBar stepBar, waterBar, workBar, dietBar;
    JLabel welcomeLbl, streakLbl;
    JTextArea challengeArea, coachArea;
    // Module 2
    JTextField aDate, aSteps, aDist; DefaultTableModel actModel; JTable actTable; List<ActivityEntry> shownAct = new ArrayList<>();
    // Module 3
    JTextField wDate, wMin; JComboBox<String> wType; JLabel wrkInfo; DefaultTableModel wrkModel; JTable wrkTable; List<WorkoutEntry> shownWrk = new ArrayList<>();
    // Module 4
    JTextField dDate, dFood, dCal; JComboBox<String> dMeal; JLabel dietInfo; DefaultTableModel mealModel; JTable mealTable; List<MealEntry> shownMeal = new ArrayList<>();
    JTextField hDate, hWeight, hSleep, hWater; DefaultTableModel hltModel; JTable hltTable; List<HealthEntry> shownHlt = new ArrayList<>();
    // Module 5
    JTextField gSteps, gWater, gWork; final ChartPanel chart = new ChartPanel(); JTextArea weeklyArea; DefaultTableModel progModel;
    // Module 6
    JTextArea reportArea;
    // Module 1
    JTextField pName, pAge, pH, pW; JComboBox<String> pGender; JLabel bmiLbl;

    FitPulse(UserData data) {
        this.data = data;
        setTitle("FitPulse - Smart Fitness Habit Coach");
        setSize(1180, 820); setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BG); setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        content.setBackground(Theme.BG); content.setBorder(new EmptyBorder(6, 16, 6, 16));
        content.add(buildHome(), NAV[0]);
        content.add(buildUserTab(), NAV[1]);
        content.add(buildActivityTab(), NAV[2]);
        content.add(buildWorkoutTab(), NAV[3]);
        content.add(buildDietHealthTab(), NAV[4]);
        content.add(buildGoalsTab(), NAV[5]);
        content.add(buildReportTab(), NAV[6]);
        add(content, BorderLayout.CENTER);
        JPanel sb = new JPanel(new BorderLayout()); sb.setBackground(Theme.BG); sb.setBorder(new EmptyBorder(2, 20, 8, 20));
        sb.add(status, BorderLayout.WEST);
        add(sb, BorderLayout.SOUTH);
        loadProfileFields();
        refresh();
        setVisible(true);
    }

    JPanel buildHeader() {
        JPanel head = new JPanel(new BorderLayout()); head.setBackground(Theme.BG); head.setBorder(new EmptyBorder(12, 20, 2, 16));
        JPanel titles = new JPanel(new GridLayout(2, 1)); titles.setOpaque(false);
        titles.add(UI.label("FitPulse", 28, Font.BOLD, Theme.CYAN));
        titles.add(UI.label("Smart Fitness Habit Coach", 12, Font.PLAIN, Theme.MUTED));
        head.add(titles, BorderLayout.WEST);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 8)); nav.setOpaque(false);
        for (String n : NAV) {
            JButton b = UI.button(n, Theme.FIELD); b.setBorder(new EmptyBorder(8, 10, 8, 10));
            b.addActionListener(e -> { refresh(); cards.show(content, n); });
            nav.add(b);
        }
        head.add(nav, BorderLayout.EAST);
        return head;
    }

    /* ---------------------------- HOME ---------------------------- */
    JPanel buildHome() {
        JPanel p = new JPanel(new BorderLayout(14, 14)); p.setOpaque(false);
        JPanel top = new JPanel(new GridLayout(1, 2, 14, 0)); top.setOpaque(false);
        JPanel ringCard = UI.card(); ringCard.setLayout(new BorderLayout());
        welcomeLbl = UI.label("", 16, Font.BOLD, Theme.TEXT);
        ringCard.add(welcomeLbl, BorderLayout.NORTH); ringCard.add(ring, BorderLayout.CENTER);
        top.add(ringCard);

        JPanel gc = UI.card(); gc.setLayout(new BoxLayout(gc, BoxLayout.Y_AXIS));
        gc.add(UI.label("TODAY'S GOALS (adaptive)", 13, Font.BOLD, Theme.MUTED)); gc.add(Box.createVerticalStrut(10));
        stepBar = UI.bar(Theme.CYAN); waterBar = UI.bar(Theme.BLUE); workBar = UI.bar(Theme.PINK); dietBar = UI.bar(Theme.LIME);
        String[] ln = {"Steps", "Water", "Workout", "Diet (calories)"}; JProgressBar[] bs = {stepBar, waterBar, workBar, dietBar};
        for (int i = 0; i < 4; i++) {
            JLabel l = UI.label(ln[i], 13, Font.BOLD, Theme.TEXT); l.setAlignmentX(Component.LEFT_ALIGNMENT);
            bs[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            gc.add(l); gc.add(Box.createVerticalStrut(3)); gc.add(bs[i]); gc.add(Box.createVerticalStrut(9));
        }
        streakLbl = UI.label("", 20, Font.BOLD, Theme.LIME); streakLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        gc.add(Box.createVerticalStrut(4)); gc.add(streakLbl); gc.add(Box.createVerticalStrut(10));
        JLabel dc = UI.label("DAILY CHALLENGE", 13, Font.BOLD, Theme.MUTED); dc.setAlignmentX(Component.LEFT_ALIGNMENT);
        gc.add(dc); gc.add(Box.createVerticalStrut(4));
        challengeArea = UI.area(3); challengeArea.setForeground(Theme.CYAN); challengeArea.setFont(Theme.font(Font.BOLD, 14));
        challengeArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        gc.add(challengeArea);
        top.add(gc);
        p.add(top, BorderLayout.CENTER);

        coachArea = UI.area(6);
        JPanel coach = UI.section("COACH SAYS", null, coachArea);
        p.add(coach, BorderLayout.SOUTH);
        return p;
    }

    /* -------------------- MODULE 1 : USER MANAGEMENT -------------------- */
    JPanel buildUserTab() {
        JPanel wrap = new JPanel(new GridBagLayout()); wrap.setOpaque(false);
        JPanel c = UI.card(); c.setLayout(new GridBagLayout());
        GridBagConstraints gb = new GridBagConstraints();
        gb.insets = new Insets(7, 8, 7, 8); gb.anchor = GridBagConstraints.WEST; gb.fill = GridBagConstraints.HORIZONTAL;
        pName = UI.field(16); pAge = UI.field(16); pH = UI.field(16); pW = UI.field(16); pGender = UI.combo(new String[]{"Female", "Male"});
        gb.gridx = 0; gb.gridy = 0; gb.gridwidth = 2;
        c.add(UI.label("User Management", 20, Font.BOLD, Theme.CYAN), gb); gb.gridwidth = 1;
        String[] n = {"Name", "Age", "Gender", "Height (cm)", "Weight (kg) - profile"};
        JComponent[] f = {pName, pAge, pGender, pH, pW};
        for (int i = 0; i < n.length; i++) {
            gb.gridx = 0; gb.gridy = i + 1; gb.weightx = 0; c.add(UI.label(n[i], 14, Font.PLAIN, Theme.TEXT), gb);
            gb.gridx = 1; gb.weightx = 1; c.add(f[i], gb);
        }
        bmiLbl = UI.label("", 15, Font.BOLD, Theme.LIME);
        gb.gridx = 0; gb.gridy = 6; gb.gridwidth = 2; c.add(bmiLbl, gb);
        JPanel b1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); b1.setOpaque(false);
        JButton save = UI.button("Save Profile", Theme.CYAN), pinB = UI.button("Change PIN", Theme.FIELD), sw = UI.button("Switch User", Theme.FIELD);
        save.addActionListener(e -> saveProfile()); pinB.addActionListener(e -> changePin()); sw.addActionListener(e -> switchUser());
        b1.add(save); b1.add(pinB); b1.add(sw);
        gb.gridy = 7; c.add(b1, gb);
        JPanel b2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); b2.setOpaque(false);
        JButton demo = UI.button("Load Demo Data (14 days)", Theme.LIME), clr = UI.button("Clear My Records", Theme.PINK), del = UI.button("Delete Account", Theme.PINK);
        demo.addActionListener(e -> loadDemo()); clr.addActionListener(e -> clearRecords()); del.addActionListener(e -> deleteAccount());
        b2.add(demo); b2.add(clr); b2.add(del);
        gb.gridy = 8; c.add(b2, gb);
        wrap.add(c);
        return wrap;
    }
    void loadProfileFields() {
        User u = data.user;
        pName.setText(u.name); pAge.setText("" + u.age); pH.setText("" + u.height); pW.setText("" + u.weight); pGender.setSelectedItem(u.gender);
    }
    void saveProfile() {
        try {
            String nm = pName.getText().trim();
            if (nm.isEmpty()) throw new InvalidInputException("Name cannot be empty.");
            data.user.name = nm; data.user.age = UI.parseInt(pAge.getText(), "Age", 5, 120);
            data.user.height = UI.parseDouble(pH.getText(), "Height", 80, 250); data.user.weight = UI.parseDouble(pW.getText(), "Weight", 20, 300);
            data.user.gender = (String) pGender.getSelectedItem();
            FileStore.updateUser(data.user);
            done("Profile saved.");
        } catch (InvalidInputException ex) { warn(ex.getMessage());
        } catch (IOException ex) { JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage()); }
    }
    void changePin() {
        JPasswordField oldP = UI.pin(10), newP = UI.pin(10);
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8)); p.setBackground(Theme.BG);
        p.add(UI.label("Current PIN", 13, Font.PLAIN, Theme.TEXT)); p.add(oldP);
        p.add(UI.label("New 4-digit PIN", 13, Font.PLAIN, Theme.TEXT)); p.add(newP);
        if (JOptionPane.showConfirmDialog(this, p, "Change PIN", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (data.user.pinHash != User.hash(new String(oldP.getPassword()))) { warn("Current PIN is wrong."); return; }
        String np = new String(newP.getPassword());
        if (!np.matches("\\d{4}")) { warn("PIN must be exactly 4 digits."); return; }
        data.user.pinHash = User.hash(np);
        try { FileStore.updateUser(data.user); done("PIN changed."); } catch (IOException e) { warn("Could not save: " + e.getMessage()); }
    }
    void switchUser() { persist(); dispose(); start(); }
    void deleteAccount() {
        if (JOptionPane.showConfirmDialog(this, "Delete account '" + data.user.username + "' and ALL its records?", "Delete account", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { FileStore.deleteUser(data.user.username); } catch (IOException e) { warn("Could not delete: " + e.getMessage()); return; }
        dispose(); start();
    }
    void loadDemo() {
        if (JOptionPane.showConfirmDialog(this, "Replace your records with 14 days of demo data?", "Demo data", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Demo.fill(data); persist(); refresh(); cards.show(content, NAV[0]); status.setText("Demo data loaded.");
    }
    void clearRecords() {
        if (JOptionPane.showConfirmDialog(this, "Delete ALL your activity, workout, diet and health records?", "Clear records", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        data.clearRecords(); persist(); refresh(); status.setText("Records cleared.");
    }

    /* ---------------- MODULE 2 : FITNESS ACTIVITY TRACKING --------------- */
    JPanel buildActivityTab() {
        aDate = UI.dateField(); aSteps = UI.field(8); aDist = UI.field(8);
        JPanel f = UI.row();
        f.add(UI.label("Date", 13, Font.PLAIN, Theme.TEXT)); f.add(aDate);
        f.add(UI.label("Steps", 13, Font.PLAIN, Theme.TEXT)); f.add(aSteps);
        f.add(UI.label("Distance km (blank = auto)", 13, Font.PLAIN, Theme.TEXT)); f.add(aDist);
        JButton add = UI.button("Save Activity", Theme.CYAN), del = UI.button("Delete Selected", Theme.PINK);
        add.addActionListener(e -> addActivity()); del.addActionListener(e -> deleteActivity());
        f.add(add); f.add(del);
        actModel = UI.model("Date", "Steps", "Distance (km)", "Calories from steps");
        actTable = UI.table(actModel);
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        p.add(UI.section("FITNESS ACTIVITY TRACKING   (one entry per day; saving a date again updates it)", f, UI.scroll(actTable)), BorderLayout.CENTER);
        return p;
    }
    void addActivity() {
        try {
            LocalDate d = UI.date(aDate);
            int steps = UI.parseInt(aSteps.getText(), "Steps", 0, 100000);
            String ds = aDist.getText().trim();
            double dist = ds.isEmpty() ? steps * 0.00075 : UI.parseDouble(ds, "Distance", 0, 200);
            data.snapshot(d);
            data.activities.removeIf(a -> a.date.equals(d));
            data.activities.add(new ActivityEntry(d, steps, dist));
            aSteps.setText(""); aDist.setText("");
            doneFor("Activity saved.", d);
        } catch (InvalidInputException ex) { warn(ex.getMessage()); }
    }
    void deleteActivity() {
        int r = actTable.getSelectedRow();
        if (r < 0) { warn("Select a row first."); return; }
        data.activities.remove(shownAct.get(r)); done("Activity deleted.");
    }

    /* -------------------- MODULE 3 : WORKOUT MANAGEMENT ------------------ */
    JPanel buildWorkoutTab() {
        wDate = UI.dateField(); wMin = UI.field(6); wType = UI.combo(Workout.TYPES);
        wrkInfo = UI.label(" ", 13, Font.ITALIC, Theme.CYAN);
        wType.addActionListener(e -> wrkInfo.setText("Tip: " + Workout.of((String) wType.getSelectedItem()).tip()));
        wrkInfo.setText("Tip: " + Workout.of((String) wType.getSelectedItem()).tip());
        JPanel f = UI.row();
        f.add(UI.label("Date", 13, Font.PLAIN, Theme.TEXT)); f.add(wDate);
        f.add(UI.label("Workout", 13, Font.PLAIN, Theme.TEXT)); f.add(wType);
        f.add(UI.label("Minutes", 13, Font.PLAIN, Theme.TEXT)); f.add(wMin);
        JButton add = UI.button("Add Workout", Theme.CYAN), del = UI.button("Delete Selected", Theme.PINK);
        add.addActionListener(e -> addWorkout()); del.addActionListener(e -> deleteWorkout());
        f.add(add); f.add(del);
        JPanel form = new JPanel(); form.setOpaque(false); form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        f.setAlignmentX(Component.LEFT_ALIGNMENT); wrkInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(f); form.add(wrkInfo);
        wrkModel = UI.model("Date", "Workout", "Minutes", "Calories burned");
        wrkTable = UI.table(wrkModel);
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        p.add(UI.section("WORKOUT MANAGEMENT   (add many workouts per day)", form, UI.scroll(wrkTable)), BorderLayout.CENTER);
        return p;
    }
    void addWorkout() {
        try {
            LocalDate d = UI.date(wDate);
            int mins = UI.parseInt(wMin.getText(), "Minutes", 1, 600);
            String type = (String) wType.getSelectedItem();
            Workout w = Workout.of(type);                                   // polymorphism
            double cal = w.caloriesBurned(data.currentWeight(d), mins);
            data.snapshot(d);
            data.workouts.add(new WorkoutEntry(d, type, mins, cal));
            wMin.setText("");
            doneFor(String.format("%s %d min added (%.0f kcal).", type, mins, cal), d);
        } catch (InvalidInputException ex) { warn(ex.getMessage()); }
    }
    void deleteWorkout() {
        int r = wrkTable.getSelectedRow();
        if (r < 0) { warn("Select a row first."); return; }
        data.workouts.remove(shownWrk.get(r)); done("Workout deleted.");
    }

    /* ---------------- MODULE 4 : DIET & HEALTH TRACKING ------------------ */
    JPanel buildDietHealthTab() {
        // diet
        dDate = UI.dateField(); dFood = UI.field(14); dCal = UI.field(6); dMeal = UI.combo(new String[]{"Breakfast", "Lunch", "Snack", "Dinner"});
        JPanel f = UI.row();
        f.add(UI.label("Date", 13, Font.PLAIN, Theme.TEXT)); f.add(dDate);
        f.add(dMeal); f.add(UI.label("Food", 13, Font.PLAIN, Theme.TEXT)); f.add(dFood);
        f.add(UI.label("kcal", 13, Font.PLAIN, Theme.TEXT)); f.add(dCal);
        JButton add = UI.button("Add Meal", Theme.CYAN), del = UI.button("Delete", Theme.PINK);
        add.addActionListener(e -> addMeal()); del.addActionListener(e -> deleteMeal());
        f.add(add); f.add(del);
        dietInfo = UI.label(" ", 13, Font.BOLD, Theme.LIME); dietInfo.setAlignmentX(Component.LEFT_ALIGNMENT); f.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel form = new JPanel(); form.setOpaque(false); form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(f); form.add(dietInfo);
        mealModel = UI.model("Date", "Meal", "Food", "kcal"); mealTable = UI.table(mealModel);
        JPanel diet = UI.section("DIET TRACKING - meals & calorie intake", form, UI.scroll(mealTable));

        // health
        hDate = UI.dateField(); hWeight = UI.field(6); hSleep = UI.field(5); hWater = UI.field(5);
        JPanel hf = UI.row();
        hf.add(UI.label("Date", 13, Font.PLAIN, Theme.TEXT)); hf.add(hDate);
        hf.add(UI.label("Weight kg", 13, Font.PLAIN, Theme.TEXT)); hf.add(hWeight);
        hf.add(UI.label("Sleep h", 13, Font.PLAIN, Theme.TEXT)); hf.add(hSleep);
        hf.add(UI.label("Water glasses", 13, Font.PLAIN, Theme.TEXT)); hf.add(hWater);
        JButton hs = UI.button("Save Health", Theme.CYAN), hd = UI.button("Delete", Theme.PINK);
        hs.addActionListener(e -> saveHealth()); hd.addActionListener(e -> deleteHealth());
        hf.add(hs); hf.add(hd);
        hltModel = UI.model("Date", "Weight (kg)", "BMI", "Sleep (h)", "Water (glasses)"); hltTable = UI.table(hltModel);
        JPanel health = UI.section("HEALTH TRACKING - weight, BMI, sleep, water   (blank fields keep earlier values for that date)", hf, UI.scroll(hltTable));

        JPanel p = new JPanel(new GridLayout(2, 1, 0, 12)); p.setOpaque(false);
        p.add(diet); p.add(health);
        return p;
    }
    void addMeal() {
        try {
            LocalDate d = UI.date(dDate);
            String food = dFood.getText().trim();
            if (food.isEmpty()) throw new InvalidInputException("Enter the food name.");
            int cal = UI.parseInt(dCal.getText(), "Calories", 1, 5000);
            data.snapshot(d);
            data.meals.add(new MealEntry(d, (String) dMeal.getSelectedItem(), food, cal));
            dFood.setText(""); dCal.setText("");
            doneFor("Meal added.", d);
        } catch (InvalidInputException ex) { warn(ex.getMessage()); }
    }
    void deleteMeal() {
        int r = mealTable.getSelectedRow();
        if (r < 0) { warn("Select a meal first."); return; }
        data.meals.remove(shownMeal.get(r)); done("Meal deleted.");
    }
    void saveHealth() {
        try {
            LocalDate d = UI.date(hDate);
            HealthEntry old = null;
            for (HealthEntry h : data.health) if (h.date.equals(d)) old = h;
            double w = hWeight.getText().trim().isEmpty() ? (old == null ? 0 : old.weight) : UI.parseDouble(hWeight.getText(), "Weight", 20, 300);
            double s = hSleep.getText().trim().isEmpty() ? (old == null ? 0 : old.sleep) : UI.parseDouble(hSleep.getText(), "Sleep", 0, 24);
            int wa = hWater.getText().trim().isEmpty() ? (old == null ? 0 : old.water) : UI.parseInt(hWater.getText(), "Water glasses", 0, 30);
            if (w == 0 && s == 0 && wa == 0) throw new InvalidInputException("Enter at least one of weight, sleep or water.");
            data.snapshot(d);
            data.health.removeIf(h -> h.date.equals(d));
            data.health.add(new HealthEntry(d, w, s, wa));
            hWeight.setText(""); hSleep.setText(""); hWater.setText("");
            doneFor("Health data saved.", d);
        } catch (InvalidInputException ex) { warn(ex.getMessage()); }
    }
    void deleteHealth() {
        int r = hltTable.getSelectedRow();
        if (r < 0) { warn("Select a row first."); return; }
        data.health.remove(shownHlt.get(r)); done("Health entry deleted.");
    }

    /* ------------- MODULE 5 : GOAL & PROGRESS MANAGEMENT ----------------- */
    JPanel buildGoalsTab() {
        gSteps = UI.field(6); gWater = UI.field(4); gWork = UI.field(4);
        JPanel f = UI.row();
        f.add(UI.label("Steps", 13, Font.PLAIN, Theme.TEXT)); f.add(gSteps);
        f.add(UI.label("Water (glasses)", 13, Font.PLAIN, Theme.TEXT)); f.add(gWater);
        f.add(UI.label("Workout (min)", 13, Font.PLAIN, Theme.TEXT)); f.add(gWork);
        JButton apply = UI.button("Set My Goals (from today)", Theme.CYAN);
        apply.addActionListener(e -> applyGoals());
        f.add(apply);
        JPanel goalCard = UI.section("GOALS   (the coach adapts them daily from your last 7 days)", f, new JPanel() {{ setOpaque(false); setPreferredSize(new Dimension(1, 0)); }});

        weeklyArea = UI.area(8);
        JPanel mid = new JPanel(new GridLayout(1, 2, 12, 0)); mid.setOpaque(false);
        JPanel cc = UI.card(); cc.setLayout(new BorderLayout()); cc.add(chart, BorderLayout.CENTER);
        mid.add(cc);
        mid.add(UI.section("WEEK vs LAST WEEK", null, weeklyArea));

        progModel = UI.model("Date", "Score", "Steps / goal", "Water / goal", "Workout / goal", "Intake / target");
        JTable pt = UI.table(progModel);
        JScrollPane sp = UI.scroll(pt); sp.setPreferredSize(new Dimension(100, 170));
        JPanel bottom = UI.section("DAILY PROGRESS (newest first)", null, sp);

        JPanel p = new JPanel(new BorderLayout(0, 10)); p.setOpaque(false);
        p.add(goalCard, BorderLayout.NORTH); p.add(mid, BorderLayout.CENTER); p.add(bottom, BorderLayout.SOUTH);
        return p;
    }
    void applyGoals() {
        try {
            Goals g = new Goals(UI.parseInt(gSteps.getText(), "Step goal", 1000, 30000),
                    UI.parseInt(gWater.getText(), "Water goal", 1, 20), UI.parseInt(gWork.getText(), "Workout goal", 5, 300));
            data.baseGoals = g; data.goalLog.put(LocalDate.now(), g);
            done("Goals updated for today; the coach will adapt them from here.");
        } catch (InvalidInputException ex) { warn(ex.getMessage()); }
    }

    /* ------------- MODULE 6 : REPORTS & RECOMMENDATIONS ------------------ */
    JPanel buildReportTab() {
        reportArea = UI.area(20); reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JPanel f = UI.row();
        JButton exp = UI.button("Export Report to .txt", Theme.LIME);
        exp.addActionListener(e -> exportReport());
        f.add(exp);
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        p.add(UI.section("WEEKLY REPORT & RECOMMENDATIONS", f, UI.scroll(reportArea)), BorderLayout.CENTER);
        return p;
    }
    void exportReport() {
        String name = "FitPulse_Report_" + data.user.username + "_" + LocalDate.now() + ".txt";
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(name)))) {
            pw.print(Coach.weeklyReport(data, LocalDate.now()));
            JOptionPane.showMessageDialog(this, "Report saved:\n" + new File(name).getAbsolutePath());
        } catch (IOException e) { warn("Could not write report: " + e.getMessage()); }
    }

    /* ------------------------- shared actions ---------------------------- */
    void warn(String m) { JOptionPane.showMessageDialog(this, m, "Please check", JOptionPane.WARNING_MESSAGE); }
    void persist() {
        try { FileStore.saveData(data); }
        catch (IOException e) { JOptionPane.showMessageDialog(this, "Could not save data file: " + e.getMessage(), "File error", JOptionPane.ERROR_MESSAGE); }
    }
    void done(String msg) { persist(); refresh(); status.setText(msg); }
    void doneFor(String msg, LocalDate d) {
        persist(); refresh();
        status.setText(msg + String.format("   Score for %s: %.0f / 100", d, data.summary(d).score()));
    }

    static void setBar(JProgressBar b, int v, int goal, String unit) {
        b.setValue((int) Math.min(100, 100.0 * v / Math.max(goal, 1)));
        b.setString(String.format("%,d / %,d %s", v, goal, unit));
    }

    /* ----------------------------- REFRESH ------------------------------- */
    void refresh() {
        LocalDate today = LocalDate.now();
        DaySummary t = data.summary(today);
        LocalDate ld = data.latestDate();
        DaySummary shown = t.any ? t : (ld == null ? null : data.summary(ld));
        double wk = data.weekAvg(today);

        welcomeLbl.setText("Hi, " + data.user.name + "!");
        ring.update(shown == null ? 0 : shown.score(),
                t.any ? "Today's score" : shown == null ? "Log your first day to light the pulse" : "Last logged: " + shown.date, UserData.rank(wk));
        setBar(stepBar, t.steps, t.goals.steps, "steps"); setBar(waterBar, t.water, t.goals.water, "glasses");
        setBar(workBar, t.workoutMin, t.goals.workout, "min"); setBar(dietBar, t.intake, t.calTarget, "kcal");
        streakLbl.setText("<html>Streak: " + data.streak(today) + " day(s)<br><span style='font-size:12px;color:#8A94AD'>7-day average: " + Math.round(wk) + "</span></html>");
        challengeArea.setText(Coach.challenge(data, today));

        StringBuilder sb = new StringBuilder();
        if (shown == null) sb.append("Welcome to FitPulse! Log activity, workouts and meals - or open 'User Management' and load demo data to see the coach in action.");
        else {
            sb.append(Coach.suggestion(data, shown));
            Goals tom = data.goalsOn(shown.date.plusDays(1));
            sb.append("\nTarget for ").append(shown.date.plusDays(1)).append(": ").append(Coach.fmt(tom.steps)).append(" steps | ")
              .append(tom.water).append(" glasses | ").append(tom.workout).append(" min workout | ~").append(Coach.fmt(data.calorieTarget(shown.date.plusDays(1)))).append(" kcal");
            WorkoutEntry lw = null;
            for (WorkoutEntry w : data.workouts) if (w.date.equals(shown.date)) lw = w;
            if (lw != null) sb.append("\nTip: ").append(Workout.of(lw.type).tip());
        }
        coachArea.setText(sb.toString());

        // module 2 table
        shownAct = new ArrayList<>(data.activities); shownAct.sort((a, b) -> b.date.compareTo(a.date));
        actModel.setRowCount(0);
        for (ActivityEntry a : shownAct)
            actModel.addRow(new Object[]{a.date, String.format("%,d", a.steps), String.format("%.2f", a.distance), String.format("%.0f", a.steps * 0.04 * (data.currentWeight(a.date) / 70.0))});
        // module 3 table
        shownWrk = new ArrayList<>(data.workouts); shownWrk.sort((a, b) -> b.date.compareTo(a.date));
        wrkModel.setRowCount(0);
        for (WorkoutEntry w : shownWrk) wrkModel.addRow(new Object[]{w.date, w.type, w.minutes, String.format("%.0f", w.calories)});
        // module 4 tables
        shownMeal = new ArrayList<>(data.meals); shownMeal.sort((a, b) -> b.date.compareTo(a.date));
        mealModel.setRowCount(0);
        for (MealEntry m : shownMeal) mealModel.addRow(new Object[]{m.date, m.meal, m.food, m.calories});
        dietInfo.setText(String.format("Today: intake %,d kcal  |  target ~%,d kcal  |  burned %.0f kcal  |  net %+,.0f kcal", t.intake, t.calTarget, t.burned, t.intake - t.burned));
        shownHlt = new ArrayList<>(data.health); shownHlt.sort((a, b) -> b.date.compareTo(a.date));
        hltModel.setRowCount(0);
        for (HealthEntry h : shownHlt)
            hltModel.addRow(new Object[]{h.date, h.weight > 0 ? h.weight : "-", h.weight > 0 ? String.format("%.1f", data.user.bmi(h.weight)) : "-", h.sleep > 0 ? h.sleep : "-", h.water > 0 ? h.water : "-"});
        // module 5
        gSteps.setText("" + t.goals.steps); gWater.setText("" + t.goals.water); gWork.setText("" + t.goals.workout);
        double[] s = new double[7]; String[] l = new String[7];
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(6 - i); DaySummary x = data.summary(d);
            s[i] = x.any ? x.score() : -1; l[i] = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        }
        chart.setData(s, l);
        double lastW = data.weekAvg(today.minusDays(7)), diff = wk - lastW; int days = 0, steps = 0; double cal = 0;
        for (int i = 0; i < 7; i++) { DaySummary x = data.summary(today.minusDays(i)); if (x.completed()) days++; steps += x.steps; cal += x.burned; }
        String verdict = data.latestDate() == null ? "No data yet." : diff > 3 ? "You are improving - keep the momentum!"
                : diff < -3 ? "Slight dip this week - the coach keeps targets realistic to rebuild your streak." : "Steady week - consistency is the real superpower.";
        weeklyArea.setText(String.format("This week average : %.1f%nLast week average : %.1f   (change %+.1f)%nDays above streak line : %d / 7%nTotal steps : %,d%nCalories burned : %.0f kcal%n%n%s",
                wk, lastW, diff, days, steps, cal, verdict));
        progModel.setRowCount(0);
        for (int i = 0, shownRows = 0; i < 60 && shownRows < 14; i++) {
            DaySummary x = data.summary(today.minusDays(i));
            if (!x.any) continue; shownRows++;
            progModel.addRow(new Object[]{x.date, String.format("%.0f%s", x.score(), x.completed() ? " *" : ""),
                    x.steps + " / " + x.goals.steps, x.water + " / " + x.goals.water, x.workoutMin + " / " + x.goals.workout, x.intake + " / " + x.calTarget});
        }
        // module 6
        reportArea.setText(Coach.weeklyReport(data, today)); reportArea.setCaretPosition(0);
        // module 1
        double cw = data.currentWeight(today);
        bmiLbl.setText(String.format("Current weight %.1f kg   |   BMI %.1f (%s)   |   Daily calorie target ~%,d kcal", cw, data.user.bmi(cw), data.user.bmiCategory(cw), data.calorieTarget(today)));
    }

    /* ------------------------------ START -------------------------------- */
    static void start() {
        LoginDialog d = new LoginDialog();
        d.setVisible(true);
        if (d.result == null) System.exit(0);
        new FitPulse(d.result);
    }
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(FitPulse::start);
    }
}
