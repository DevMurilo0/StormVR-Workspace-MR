package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test

class SpatialGestureTest {
    private fun update(g: SpatialGesture,t: Long,grip: Boolean=false,id: String?="bar",kind: FingertipGesture.Kind=FingertipGesture.Kind.MOVE,
                       seen: Boolean=true,point: Boolean=true,blocked: Boolean=false,x: Float=0f)=
        g.update(id,kind,x,0f,seen,point,grip,blocked,t,.04f)
    @Test fun closedHandAtStartupCannotCaptureAndPointArms() {
        val g=SpatialGesture(); update(g,0,true); update(g,200,true); assertFalse(g.captured)
        update(g,300); update(g,400,true); assertFalse(g.captured)
        update(g,480,true); assertTrue(g.captured)
        update(g,500,true,id="other",point=false); assertEquals("bar",g.target)
    }
    @Test fun clickAndManipulationAreExclusiveAndReleaseCannotClick() {
        val g=SpatialGesture(); update(g,0); update(g,100,true); update(g,180,true)
        assertTrue(g.captured)
        assertFalse(update(g,250)); assertFalse(update(g,330)); assertFalse(g.captured)
        assertEquals(FingertipGesture.Phase.RELEASED,g.phase)
    }
    @Test fun shortPinchClicksOnceOnReleaseAndLongHoldDoesNotClick() {
        val g=SpatialGesture(); val k=FingertipGesture.Kind.CLICK
        update(g,0,kind=k); update(g,100,true,kind=k); update(g,200,kind=k)
        assertTrue(update(g,280,kind=k)); assertFalse(update(g,300,kind=k))
        update(g,400,true,kind=k); update(g,1000,true,kind=k); update(g,1100,kind=k)
        assertFalse(update(g,1180,kind=k)); assertFalse(g.captured)
    }
    @Test fun briefLossFreezesCaptureLongLossDisarmsUntilOpen() {
        val g=SpatialGesture(); update(g,0); update(g,100,true); update(g,180,true)
        update(g,250,true,seen=false); assertTrue(g.captured)
        update(g,300,true); assertTrue(g.captured)
        update(g,521,true,seen=false); assertFalse(g.captured)
        update(g,600,true); assertFalse(g.captured)
    }
    @Test fun lossOrMovementDuringTapCancelsClick() {
        for(loss in listOf(true,false)) {
            val g=SpatialGesture(); val k=FingertipGesture.Kind.CLICK
            update(g,0,kind=k); update(g,100,true,kind=k)
            update(g,150,true,kind=k,seen=!loss,x=if(loss) 0f else .2f)
            update(g,200,kind=k); assertFalse(update(g,280,kind=k))
        }
    }
    @Test fun locksAndModeChangeCancelImmediately() {
        val g=SpatialGesture(); update(g,0); update(g,100,true); update(g,180,true)
        update(g,200,true,blocked=true); assertFalse(g.ownsTarget)
    }
}
