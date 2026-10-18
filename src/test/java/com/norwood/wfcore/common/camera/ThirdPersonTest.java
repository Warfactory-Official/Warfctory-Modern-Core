package com.norwood.wfcore.common.camera;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirdPersonTest {

    @AfterEach
    void reset() {
        ThirdPersonPolicy.reset();
    }

    @Test
    void lockedUntilAServerSaysOtherwise() {
        assertFalse(ThirdPersonPolicy.allowed());
        assertTrue(ThirdPersonPolicy.forcesFirstPerson(false));
    }

    @Test
    void ridersKeepTheirOwnCameraEitherWay() {
        assertFalse(ThirdPersonPolicy.forcesFirstPerson(true));
        ThirdPersonPolicy.set(true);
        assertFalse(ThirdPersonPolicy.forcesFirstPerson(true));
    }

    @Test
    void allowingReleasesTheLockAndResetRestoresIt() {
        ThirdPersonPolicy.set(true);
        assertFalse(ThirdPersonPolicy.forcesFirstPerson(false));
        ThirdPersonPolicy.reset();
        assertTrue(ThirdPersonPolicy.forcesFirstPerson(false));
    }

    @Test
    void theMessageSurvivesTheWire() {
        for (boolean value : new boolean[] {true, false}) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            ThirdPersonPolicyMessage.STREAM_CODEC.encode(buf, new ThirdPersonPolicyMessage(value));
            assertEquals(new ThirdPersonPolicyMessage(value), ThirdPersonPolicyMessage.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        }
    }

    @Test
    void anOpsChoiceIsSavedAndRestored() {
        for (boolean value : new boolean[] {true, false}) {
            ThirdPersonData data = new ThirdPersonData();
            data.setAllowed(value);
            ThirdPersonData loaded = new ThirdPersonData(data.save(new CompoundTag(), null), null);
            assertEquals(value, loaded.isAllowed());
        }
    }

    @Test
    void anUnsetWorldSavesNothingSoTheConfigDefaultStaysInForce() {
        assertFalse(new ThirdPersonData().save(new CompoundTag(), null).contains("Allowed"));
    }
}
