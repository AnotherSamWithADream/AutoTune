package com.autotune.benchmark;

import com.autotune.benchmark.hardware.HardwareProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HardwareProfile Record")
class HardwareProfileTest {

    static HardwareProfile createTestProfile() {
        return new HardwareProfile(
                "NVIDIA GeForce RTX 4070", "NVIDIA Corporation", 12288,
                "536.99", "4.6.0 NVIDIA 536.99", "NVIDIA GeForce RTX 4070/PCIe/SSE2",
                29.15f, "ada_lovelace", 2022, 4,
                "AMD Ryzen 7 7800X3D", 8, 16, 4.2f, 5.0f, 96, "zen4_vcache", 5,
                32768, 28000, 8192, 4096,
                "2560x1440", 2560, 1440, 165,
                "SSD", 500_000_000_000L,
                45.0, 55.0, false,
                "21.0.1", "Eclipse Adoptium", "Windows 11", "amd64",
                List.of("GL_ARB_multitexture", "GL_ARB_vertex_buffer_object")
        );
    }

    @Test
    @DisplayName("Record fields are accessible")
    void testFieldAccess() {
        HardwareProfile profile = createTestProfile();
        assertEquals("NVIDIA GeForce RTX 4070", profile.gpuName());
        assertEquals("NVIDIA Corporation", profile.gpuVendor());
        assertEquals(12288, profile.gpuVramMb());
        assertEquals(29.15f, profile.gpuTflops(), 0.01f);
        assertEquals("ada_lovelace", profile.gpuArchitecture());
        assertEquals("AMD Ryzen 7 7800X3D", profile.cpuName());
        assertEquals(8, profile.cpuCores());
        assertEquals(16, profile.cpuThreads());
        assertEquals(96, profile.cpuL3CacheMb());
        assertEquals(32768, profile.totalRamMb());
        assertEquals(8192, profile.maxHeapMb());
        assertEquals(165, profile.displayRefreshRate());
        assertEquals("SSD", profile.storageType());
        assertFalse(profile.thermalThrottlingDetected());
    }

    @Test
    @DisplayName("Record equality by value")
    void testEquality() {
        HardwareProfile a = createTestProfile();
        HardwareProfile b = createTestProfile();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("toString is not null")
    void testToString() {
        assertNotNull(createTestProfile().toString());
    }

    @Test
    @DisplayName("GL extensions list is accessible")
    void testGlExtensions() {
        HardwareProfile profile = createTestProfile();
        assertEquals(2, profile.glExtensions().size());
        assertTrue(profile.glExtensions().contains("GL_ARB_multitexture"));
    }

    @Test
    @DisplayName("Profile with minimal/zero values doesn't crash")
    void testMinimalProfile() {
        HardwareProfile minimal = new HardwareProfile(
                "Unknown", "Unknown", 0, "", "", "", 0f, "", 0, 0,
                "Unknown", 0, 0, 0f, 0f, 0, "", 0,
                0, 0, 0, 0,
                "", 0, 0, 0,
                "", 0, 0.0, 0.0, false,
                "", "", "", "",
                List.of()
        );
        assertEquals(0, minimal.gpuVramMb());
        assertEquals(0, minimal.cpuCores());
        assertTrue(minimal.glExtensions().isEmpty());
    }
}
