package ru.srr.vpscockpit

import com.google.common.truth.Truth.assertThat
import com.jcraft.jsch.HostKeyRepository
import org.junit.Test

class HostKeyPinsTest {
    private class MapStore : PinStore {
        val map = mutableMapOf<String, String>()
        override fun get(host: String) = map[host]
        override fun put(host: String, fingerprint: String) {
            map[host] = fingerprint
        }
        override fun remove(host: String) {
            map.remove(host)
        }
    }

    private val serverKey = "server-key".toByteArray()
    private val attackerKey = "attacker-key".toByteArray()

    @Test
    fun firstContactPinsKey() {
        val store = MapStore()
        val pins = HostKeyPins(store)

        assertThat(pins.check("vps.example", serverKey)).isEqualTo(HostKeyRepository.OK)
        assertThat(store.map["vps.example"]).isEqualTo(HostKeyPins.fingerprint(serverKey))
        assertThat(pins.lastChange).isNull()
    }

    @Test
    fun samePinnedKeyPasses() {
        val store = MapStore()
        HostKeyPins(store).check("vps.example", serverKey)

        val pins = HostKeyPins(store)
        assertThat(pins.check("vps.example", serverKey)).isEqualTo(HostKeyRepository.OK)
        assertThat(pins.lastChange).isNull()
    }

    @Test
    fun changedKeyIsRejectedAndPinKept() {
        val store = MapStore()
        HostKeyPins(store).check("vps.example", serverKey)

        val pins = HostKeyPins(store)
        assertThat(pins.check("vps.example", attackerKey)).isEqualTo(HostKeyRepository.CHANGED)
        assertThat(store.map["vps.example"]).isEqualTo(HostKeyPins.fingerprint(serverKey))
        assertThat(pins.lastChange).isEqualTo(
            HostKeyPins.Change(
                "vps.example",
                HostKeyPins.fingerprint(serverKey),
                HostKeyPins.fingerprint(attackerKey)
            )
        )
    }

    @Test
    fun pinsArePerHostAndPort() {
        val store = MapStore()
        val pins = HostKeyPins(store)
        pins.check(HostKeyPins.keyFor("vps.example", 22), serverKey)

        assertThat(pins.check(HostKeyPins.keyFor("vps.example", 2222), attackerKey)).isEqualTo(HostKeyRepository.OK)
        assertThat(pins.check(HostKeyPins.keyFor("vps.example", 22), attackerKey)).isEqualTo(HostKeyRepository.CHANGED)
    }

    @Test
    fun removeAllowsNewKey() {
        val store = MapStore()
        HostKeyPins(store).check("vps.example", serverKey)
        val pins = HostKeyPins(store)
        pins.remove("vps.example", null)

        assertThat(pins.check("vps.example", attackerKey)).isEqualTo(HostKeyRepository.OK)
        assertThat(store.map["vps.example"]).isEqualTo(HostKeyPins.fingerprint(attackerKey))
    }

    @Test
    fun keyNameMatchesJschHostFormat() {
        assertThat(HostKeyPins.keyFor("1.2.3.4", 22)).isEqualTo("1.2.3.4")
        assertThat(HostKeyPins.keyFor("1.2.3.4", 2222)).isEqualTo("[1.2.3.4]:2222")
    }

    @Test
    fun fingerprintUsesOpenSshFormat() {
        assertThat(HostKeyPins.fingerprint("abc".toByteArray()))
            .isEqualTo("SHA256:ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0")
    }

    @Test
    fun tlsBypassOnlyForLocalTunnel() {
        assertThat(TunnelTls.mayProceed("https://127.0.0.1:19090/")).isTrue()
        assertThat(TunnelTls.mayProceed("https://localhost:19090/api")).isTrue()
        assertThat(TunnelTls.mayProceed("https://evil.example/")).isFalse()
        assertThat(TunnelTls.mayProceed("https://127.0.0.1.evil.example/")).isFalse()
        assertThat(TunnelTls.mayProceed("http://127.0.0.1:19090/")).isFalse()
        assertThat(TunnelTls.mayProceed(null)).isFalse()
        assertThat(TunnelTls.mayProceed("not a url")).isFalse()
    }
}
