package com.aero.tclstation;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Runs the actual Android-free state class embedded in ListeningService.java on the host JVM.
 * javac -d build/test-classes tests/TtsMicHoldTest.java &&
 * java -cp build/test-classes com.aero.tclstation.TtsMicHoldTest
 */
public final class TtsMicHoldTest {
    private static Object state;
    private static Class<?> type;

    private static Object call(String name, Class<?>[] signature, Object... args) throws Exception {
        Method method = type.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(state, args);
    }
    private static String begin(Object owner, String prefix) throws Exception {
        return (String) call("begin", new Class<?>[]{Object.class, String.class}, owner, prefix);
    }
    private static boolean finish(Object owner, String id) throws Exception {
        return (boolean) call("finish", new Class<?>[]{Object.class, String.class}, owner, id);
    }
    private static boolean cancelOwner(Object owner) throws Exception {
        return (boolean) call("cancelOwner", new Class<?>[]{Object.class}, owner);
    }

    private static boolean waiting() throws Exception {
        return (boolean) call("isWaiting", new Class<?>[]{});
    }
    private static boolean mayResume() throws Exception {
        return (boolean) call("mayResume", new Class<?>[]{});
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void fresh() throws Exception {
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        state = constructor.newInstance();
    }

    public static void main(String[] args) throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/com/aero/tclstation/ListeningService.java")), StandardCharsets.UTF_8);
        String marker = "// Android-free microphone/TTS hold state";
        int start = source.indexOf(marker);
        check(start >= 0, "Missing production TTS/microphone hold state");
        String watchdog = source.substring(source.indexOf("private String deferResponse("), source.indexOf("private void finishResponse("));
        check(watchdog.contains("speechHold.isHeld(id)") && watchdog.contains("stopSelf()"),
            "Stalled speech watchdog must stop the microphone service");
        // Activity teardown may release only its own hold after stopping its own engine.
        String activitySource = new String(Files.readAllBytes(Paths.get("src/com/aero/tclstation/MainActivity.java")), StandardCharsets.UTF_8);
        check(activitySource.contains("ListeningService.beginSpokenResponse(this)"),
            "Activity speech must acquire a hold even when hands-free is off");
        check(source.contains("private static final TtsMicHold speechHold") && source.contains("speechHold.heldIds()"),
            "A new service must inherit Activity speech already in progress");
        check(source.contains("static boolean isSpokenResponsePending(String id)")
            && source.contains("return speechHold.isHeld(id);"),
            "Standalone reply watchdog must inspect the actual held utterance");
        int watchdogStart = activitySource.indexOf("private void watchActivitySpeech(");
        check(watchdogStart >= 0, "Missing Activity-only TTS watchdog");
        String activityWatchdog = activitySource.substring(watchdogStart,
            activitySource.indexOf("private boolean applyVoiceChoice()"));
        check(activitySource.contains("watchActivitySpeech(id);")
            && activityWatchdog.contains("ListeningService.isSpokenResponsePending(id)")
            && activityWatchdog.contains("speech.stop()")
            && activityWatchdog.contains("ListeningService.cancelSpokenResponses(this)")
            && activityWatchdog.contains("android.os.Process.killProcess(android.os.Process.myPid())"),
            "A stalled Activity-only TTS reply must stop its engine before releasing holds, or fail closed");
        String activityTeardown = activitySource.substring(activitySource.indexOf("@Override protected void onDestroy()"));
        check(!activityTeardown.contains("spokenResponseFinished") && activityTeardown.contains("cancelSpokenResponses(this)"),
            "Activity teardown must release only its own hold, after TTS is stopped");
        check(activityTeardown.indexOf("speech.stop()") < activityTeardown.indexOf("cancelSpokenResponses(this)"),
            "Never release a microphone hold before stopping its TTS engine");
        check(activityTeardown.contains("if (!speechStopped) {")
            && activityTeardown.contains("StationDiagnosticLog.Event.PROCESS_EXIT_SAFETY")
            && activityTeardown.indexOf("StationDiagnosticLog.Event.PROCESS_EXIT_SAFETY") < activityTeardown.indexOf("android.os.Process.killProcess(android.os.Process.myPid())"),
            "A failed TTS stop must restart the process rather than retain an orphan hold forever");
        String serviceTeardown = source.substring(source.indexOf("@Override public void onDestroy()"));
        check(serviceTeardown.contains("if (!voiceStopped) {")
            && serviceTeardown.contains("StationDiagnosticLog.Event.PROCESS_EXIT_SAFETY")
            && serviceTeardown.indexOf("StationDiagnosticLog.Event.PROCESS_EXIT_SAFETY") < serviceTeardown.indexOf("android.os.Process.killProcess(android.os.Process.myPid())"),
            "Service TTS stop failure must also fail closed and reset orphaned state");
        String scratch = System.getenv("TMPDIR");
        check(scratch != null && !scratch.isEmpty(), "Set TMPDIR to a writable scratch directory");
        Path output = Files.createTempDirectory(Paths.get(scratch), "station-tts-hold-");
        try {
            Path java = output.resolve("TtsMicHold.java");
            Files.write(java, ("package com.aero.tclstation;\n" + source.substring(start)).getBytes(StandardCharsets.UTF_8));
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            check(compiler != null, "JDK compiler required");
            check(compiler.run(null, null, null, "-d", output.toString(), java.toString()) == 0,
                "Production state class did not compile without Android");
            try (URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL()})) {
                type = Class.forName("com.aero.tclstation.TtsMicHold", true, loader);
                Object activity = new Object();
                Object service = new Object();

                fresh();
                String orphan = begin(activity, "station-response");
                check(!mayResume(), "A reply without a completion callback must block the mic");
                check(cancelOwner(activity) && mayResume(), "A definitively stopped TTS engine can release its orphaned hold");
                check(!finish(activity, orphan), "A late callback after teardown must not change the hold");
                String serviceReply = begin(service, "station-hands-free");
                begin(activity, "station-response");
                check(!cancelOwner(activity) && waiting(), "Activity teardown must not release service-owned speech");
                check(cancelOwner(service) && mayResume(), "Service shutdown must not leave an orphaned hold after voice stops");
                check(!finish(service, serviceReply), "Late service callback after shutdown is harmless");
                System.out.println("PASS stopped engines release only their own orphaned holds");

                fresh();
                String offReply = begin(activity, "station-response");
                check(waiting() && !mayResume(), "Activity reply started while service is off must block later microphone startup");
                check(((java.util.List<?>) call("heldIds", new Class<?>[]{})).contains(offReply),
                    "Service startup must discover a pre-existing response for its watchdog");
                String replacement = begin(activity, "station-response");
                check(((java.util.List<?>) call("heldIds", new Class<?>[]{})).contains(offReply),
                    "Old reply must remain held until flush reports stop or completion");
                check(!finish(activity, replacement) && !mayResume(),
                    "Failed replacement enqueue must not reopen mic over the old reply");
                check(finish(activity, offReply) && mayResume(), "Prior reply callback releases final hold");
                System.out.println("PASS off-to-on speech handoff and failed replacement enqueue");

                fresh();
                Object rotatedActivity = new Object();
                String beforeRotation = begin(activity, "station-response");
                // The first service can be stopped and a new Activity can be created;
                // neither transition may discard a reply whose end is unknown.
                check(((java.util.List<?>) call("heldIds", new Class<?>[]{})).contains(beforeRotation)
                    && !mayResume(), "Restart after rotation must still see the old Activity reply");
                String afterRotation = begin(rotatedActivity, "station-response");
                check(!finish(rotatedActivity, beforeRotation) && !mayResume(),
                    "A replacement Activity cannot release the old Activity's speech");
                check(!finish(rotatedActivity, afterRotation) && !mayResume(),
                    "Completing new speech must not clear the old Activity's unknown speech");
                check(finish(activity, beforeRotation) && mayResume(),
                    "Only completion from the old owner clears the last hold");
                System.out.println("PASS rotation and service-off transition retain uncertain speech");

                fresh();
                String first = begin(activity, "station-response");
                String second = begin(activity, "station-response");
                check(!first.equals(second), "Overlapping speech must have distinct utterance IDs");
                check(!finish(activity, first) && waiting(), "Old onDone must not release new response");
                check(!finish(service, second) && waiting(), "A foreign callback must not release response");
                check(finish(activity, second) && mayResume(), "Only owning callback releases final response");
                check(!finish(activity, first), "Duplicate callback must be harmless");
                System.out.println("PASS overlapping utterances and stale callbacks");

                fresh();
                call("pauseForPushToTalk", new Class<?>[]{});
                check(!waiting() && !mayResume(),
                    "Speak while hands-free is off must still block a newly started service");
                call("resumeAfterPushToTalk", new Class<?>[]{});
                check(mayResume(), "Ending Speak must release a pre-service pause");
                System.out.println("PASS Speak started before hands-free service blocks its microphone");

                fresh();
                String speech = begin(service, "station-hands-free");
                call("pauseForPushToTalk", new Class<?>[]{});
                call("resumeAfterPushToTalk", new Class<?>[]{});
                check(waiting() && !mayResume(), "Speak return must not reopen mic during ongoing TTS");
                check(finish(service, speech) && mayResume(), "Mic may resume after TTS completes");
                System.out.println("PASS Speak while speech active");

                fresh();
                String background = begin(service, "station-hands-free");
                String oldActivitySpeech = begin(activity, "station-response");
                // Destroyed Activity: without its callback, its hold survives recreation.
                check(waiting() && !mayResume(), "Activity destruction must preserve pending speech hold");
                check(!finish(activity, background) && waiting(), "Activity cannot release service-owned speech");
                check(!finish(activity, oldActivitySpeech) && waiting(), "Old Activity callback cannot release service speech");
                check(finish(service, background) && mayResume(), "Service callback releases its own final hold");
                System.out.println("PASS service-owned speech survives Activity destruction");

                fresh();
                String stalled = begin(activity, "station-response");
                check((boolean) call("isHeld", new Class<?>[]{String.class}, stalled), "Watchdog must see stalled hold");
                check(!mayResume(), "Stalled TTS must fail closed rather than resume mic");
                System.out.println("PASS stalled TTS remains held for watchdog");
            }
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(output)) {
                files.sorted(Collections.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
        }
    }
}
