package jvmprobe256.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClassFileTransformerTest {

    private static byte[] readBytes(String resource) {
        try (java.io.InputStream in = AgentClassFileTransformerTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(in, "missing " + resource);
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] transform(AgentClassFileTransformer t, String internalName, byte[] bytes) {
        return t.transform(ClassLoader.getSystemClassLoader(), internalName, null, null, bytes);
    }

    @Test
    void includedClassIsInstrumented() {
        AgentConfig config = AgentConfig.parse("include=demo256,report=r.json");
        AgentClassFileTransformer t = new AgentClassFileTransformer(config);
        byte[] original = readBytes("demo256/Demo.class");
        byte[] out = transform(t, "demo256/Demo", original);
        assertNotNull(out, "included class should be instrumented");
        assertTrue(out.length > original.length);
    }

    @Test
    void excludedAndOutsideClassesAreUntouched() {
        AgentConfig config = AgentConfig.parse("include=demo256,exclude=demo256.excluded,report=r.json");
        AgentClassFileTransformer t = new AgentClassFileTransformer(config);
        assertNull(transform(t, "demo256/excluded/Hidden", readBytes("demo256/excluded/Hidden.class")));
        assertNull(transform(t, "org/other/Thing", new byte[4]));
        assertNull(transform(t, "java/lang/String", new byte[4]));
        assertNull(transform(t, "jvmprobe256/Profiler", new byte[4]));
        assertNull(transform(t, "org/objectweb/asm/ClassReader", new byte[4]));
    }

    @Test
    void nonSystemLoadersAreIgnored() {
        AgentConfig config = AgentConfig.parse("include=demo256,report=r.json");
        AgentClassFileTransformer t = new AgentClassFileTransformer(config);
        byte[] bytes = readBytes("demo256/Demo.class");
        ClassLoader custom = new ClassLoader(ClassLoader.getSystemClassLoader()) { };
        assertNull(t.transform(custom, "demo256/Demo", null, null, bytes));
        assertNull(t.transform(null, "demo256/Demo", null, null, bytes));
    }

    @Test
    void brokenClassKeepsOriginalBytesAndDoesNotThrow() {
        AgentConfig config = AgentConfig.parse("include=demo256,report=r.json");
        AgentClassFileTransformer t = new AgentClassFileTransformer(config);
        byte[] garbage = new byte[]{1, 2, 3, 4, 5};
        assertNull(transform(t, "demo256/Broken", garbage));
        // Interfaces are rejected by the SDK; the agent must keep original bytes.
        assertNull(transform(t, "demo256/DemoAPI", readBytes("demo256/DemoAPI.class")));
    }

    @Test
    void invalidAgentArgumentsAbortBeforeMain() {
        // premain must surface config errors as IllegalArgumentException.
        assertThrows(IllegalArgumentException.class,
                () -> ProbeAgent.premain("include=", null));
        assertThrows(IllegalArgumentException.class,
                () -> ProbeAgent.premain(null, null));
    }
}
