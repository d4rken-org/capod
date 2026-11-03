package eu.darken.capod.reaction.ui.popup

import eu.darken.capod.reaction.ui.popup.PopUpLifecycle.State
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PopUpLifecycleTest : BaseTest() {

    private class FakeEffects : PopUpLifecycle.Effects<String> {
        var attached = false
        var attachCount = 0
        var payload: String? = null
        var lastTarget: PopUpTarget? = null
        var touchable: Boolean? = null
        var fallbackToken: Long? = null
        var removeCount = 0
        var destroyCount = 0
        var releaseCount = 0

        var failAttach = false
        var failRemove = false
        var failSetTouchable = false

        override fun attach(payload: String, target: PopUpTarget) {
            attachCount++
            this.payload = payload
            lastTarget = target
            if (failAttach) throw IllegalStateException("addView failed")
            attached = true
            // Fresh window params
            touchable = true
        }

        override fun update(payload: String) {
            this.payload = payload
        }

        override fun setTarget(target: PopUpTarget) {
            lastTarget = target
        }

        override fun setTouchable(touchable: Boolean) {
            if (failSetTouchable) throw IllegalArgumentException("updateViewLayout failed")
            this.touchable = touchable
        }

        override fun scheduleFallback(token: Long) {
            fallbackToken = token
        }

        override fun cancelFallback() {
            fallbackToken = null
        }

        override fun destroyHost() {
            destroyCount++
        }

        override fun removeWindow() {
            removeCount++
            if (failRemove) throw IllegalArgumentException("removeView failed")
            attached = false
        }

        override fun release() {
            releaseCount++
            payload = null
            lastTarget = null
            touchable = null
        }
    }

    private val effects = FakeEffects()
    private val lifecycle = PopUpLifecycle(effects)

    @Test
    fun `show attaches once`() {
        lifecycle.show("a")

        lifecycle.state shouldBe State.SHOWN
        effects.attachCount shouldBe 1
        effects.attached shouldBe true
        effects.payload shouldBe "a"
        effects.lastTarget!!.visible shouldBe true
        effects.touchable shouldBe true
    }

    @Test
    fun `show while shown updates without re-attaching`() {
        lifecycle.show("a")
        val target = effects.lastTarget

        lifecycle.show("b")

        lifecycle.state shouldBe State.SHOWN
        effects.attachCount shouldBe 1
        effects.payload shouldBe "b"
        effects.lastTarget shouldBe target
    }

    @Test
    fun `close hides the target, stops touches and schedules the fallback`() {
        lifecycle.show("a")

        lifecycle.close()

        lifecycle.state shouldBe State.EXITING
        effects.lastTarget!!.visible shouldBe false
        effects.touchable shouldBe false
        effects.fallbackToken shouldBe effects.lastTarget!!.token
        effects.removeCount shouldBe 0
        effects.attached shouldBe true
    }

    @Test
    fun `close is idempotent`() {
        lifecycle.close()
        lifecycle.state shouldBe State.HIDDEN
        effects.removeCount shouldBe 0

        lifecycle.show("a")
        lifecycle.close()
        val exitTarget = effects.lastTarget
        val fallbackToken = effects.fallbackToken

        lifecycle.close()
        lifecycle.close()

        lifecycle.state shouldBe State.EXITING
        effects.lastTarget shouldBe exitTarget
        effects.fallbackToken shouldBe fallbackToken
        effects.removeCount shouldBe 0
    }

    @Test
    fun `exit completion with the current token removes the window`() {
        lifecycle.show("a")
        lifecycle.close()
        val token = effects.lastTarget!!.token

        lifecycle.onExitFinished(token)

        lifecycle.state shouldBe State.HIDDEN
        effects.attached shouldBe false
        effects.destroyCount shouldBe 1
        effects.releaseCount shouldBe 1
        effects.fallbackToken shouldBe null
    }

    @Test
    fun `stale exit completion does nothing`() {
        lifecycle.show("a")
        lifecycle.close()
        val staleToken = effects.lastTarget!!.token
        lifecycle.show("b")
        lifecycle.close()
        val currentToken = effects.lastTarget!!.token

        lifecycle.onExitFinished(staleToken)

        lifecycle.state shouldBe State.EXITING
        effects.attached shouldBe true
        effects.removeCount shouldBe 0
        effects.fallbackToken shouldBe currentToken

        lifecycle.onExitFinished(currentToken)
        lifecycle.state shouldBe State.HIDDEN
        effects.attached shouldBe false
    }

    @Test
    fun `stale fallback does nothing`() {
        lifecycle.show("a")
        lifecycle.close()
        val staleToken = effects.fallbackToken!!
        lifecycle.show("b")

        lifecycle.onFallback(staleToken)
        lifecycle.state shouldBe State.SHOWN
        effects.removeCount shouldBe 0

        lifecycle.close()
        lifecycle.onFallback(staleToken)
        lifecycle.state shouldBe State.EXITING
        effects.removeCount shouldBe 0
        effects.attached shouldBe true
    }

    @Test
    fun `fallback with the current token removes when the completion never comes`() {
        lifecycle.show("a")
        lifecycle.close()

        lifecycle.onFallback(effects.fallbackToken!!)

        lifecycle.state shouldBe State.HIDDEN
        effects.attached shouldBe false
        effects.releaseCount shouldBe 1
    }

    @Test
    fun `show during exit cancels the exit without a second window`() {
        lifecycle.show("a")
        lifecycle.close()
        val exitToken = effects.lastTarget!!.token

        lifecycle.show("b")

        lifecycle.state shouldBe State.SHOWN
        effects.fallbackToken shouldBe null
        effects.touchable shouldBe true
        effects.lastTarget!!.visible shouldBe true
        effects.payload shouldBe "b"
        effects.attachCount shouldBe 1

        lifecycle.onExitFinished(exitToken)
        lifecycle.state shouldBe State.SHOWN
        effects.removeCount shouldBe 0
    }

    @Test
    fun `failing removal still ends hidden and the next show attaches touchable`() {
        effects.failRemove = true
        lifecycle.show("a")
        lifecycle.close()

        lifecycle.onExitFinished(effects.lastTarget!!.token)

        lifecycle.state shouldBe State.HIDDEN
        effects.releaseCount shouldBe 1
        effects.fallbackToken shouldBe null

        effects.failRemove = false
        lifecycle.show("b")

        lifecycle.state shouldBe State.SHOWN
        effects.attachCount shouldBe 2
        effects.touchable shouldBe true
        effects.lastTarget!!.visible shouldBe true
    }

    @Test
    fun `failing touchable update during close still schedules the fallback`() {
        lifecycle.show("a")
        effects.failSetTouchable = true

        lifecycle.close()

        lifecycle.state shouldBe State.EXITING
        effects.lastTarget!!.visible shouldBe false
        effects.fallbackToken shouldBe effects.lastTarget!!.token

        lifecycle.onFallback(effects.fallbackToken!!)
        lifecycle.state shouldBe State.HIDDEN
    }

    @Test
    fun `failing attach leaves the lifecycle hidden and disposes the host`() {
        effects.failAttach = true

        lifecycle.show("a")

        lifecycle.state shouldBe State.HIDDEN
        effects.destroyCount shouldBe 1
        effects.releaseCount shouldBe 1

        lifecycle.close()
        lifecycle.state shouldBe State.HIDDEN

        effects.failAttach = false
        lifecycle.show("b")
        lifecycle.state shouldBe State.SHOWN
        effects.attachCount shouldBe 2
    }
}
