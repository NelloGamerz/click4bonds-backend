package com.click4bonds.testing;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import com.click4bonds.app.Modules.OTP.Model.OtpData;
import com.click4bonds.app.Modules.OTP.Model.OtpType;

/**
 * Produces an {@code OtpData} that looks identical to the application's but is
 * a genuinely different {@link Class} — the situation Spring Boot DevTools'
 * restart classloader creates in a running application.
 *
 * <p>Useful for proving that stored values survive being written by a process
 * whose classes came from a different classloader, and that nothing downstream
 * depends on JVM class identity.</p>
 *
 * <p>Lives outside {@code com.click4bonds.app} so {@code @SpringBootTest}'s
 * component scan never sees it.</p>
 */
public final class ForeignOtpData {

    private static final String OTP_DATA = "com.click4bonds.app.Modules.OTP.Model.OtpData";

    private ForeignOtpData() {
    }

    /**
     * @return the second copy of {@code OtpData}, linked against the same
     *         {@code OtpType} and JDK types as the application's
     */
    public static Class<?> type() throws ClassNotFoundException {
        return Class.forName(OTP_DATA, true, new IsolatingClassLoader(ForeignOtpData.class.getClassLoader()));
    }

    /**
     * Fails the caller if the class is not actually a distinct copy, so a test
     * built on this helper can never pass vacuously.
     */
    public static Class<?> verifiedType() throws ClassNotFoundException {

        Class<?> foreign = type();

        if (foreign == OtpData.class) {
            throw new IllegalStateException(
                    "Expected a second copy of OtpData but got the application's own Class");
        }

        return foreign;
    }

    /**
     * @return an instance of the foreign {@code OtpData}
     */
    public static Object instance(
            String otpHash,
            String identifier,
            OtpType otpType,
            Instant createdAt,
            Instant expiresAt,
            int attemptCount) throws Exception {

        return verifiedType()
                .getDeclaredConstructor(
                        String.class, String.class, OtpType.class,
                        Instant.class, Instant.class, int.class)
                .newInstance(otpHash, identifier, otpType, createdAt, expiresAt, attemptCount);
    }

    /**
     * Loads one class a second time. Every other type still resolves through
     * the parent, so the copy links against the application's {@code OtpType}
     * rather than dragging a second copy of the world along with it.
     */
    private static final class IsolatingClassLoader extends ClassLoader {

        private final String isolatedClass;

        IsolatingClassLoader(ClassLoader parent) {
            this(OTP_DATA, parent);
        }

        private IsolatingClassLoader(String isolatedClass, ClassLoader parent) {
            super(parent);
            this.isolatedClass = isolatedClass;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

            if (!isolatedClass.equals(name)) {
                return super.loadClass(name, resolve);
            }

            Class<?> loaded = findLoadedClass(name);

            if (loaded != null) {
                return loaded;
            }

            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {

                byte[] bytes = in.readAllBytes();
                Class<?> defined = defineClass(name, bytes, 0, bytes.length);

                if (resolve) {
                    resolveClass(defined);
                }

                return defined;

            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }
}
