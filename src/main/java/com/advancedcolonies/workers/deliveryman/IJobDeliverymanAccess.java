package com.advancedcolonies.workers.deliveryman;

import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;

import java.util.LinkedList;

/**
 * Bridge interface implemented by {@link com.advancedcolonies.mixin.workers.deliveryman.JobDeliverymanMixin}
 * to access internal queue and delivery comparison helpers across MineColonies versions (1.1.1285+ and 1.1.1403+).
 */
public interface IJobDeliverymanAccess {

    LinkedList<IToken<?>> advancedColonies$getMutableTaskQueue();

    boolean advancedColonies$haveTasksSameSourceAndDest(Delivery requestA, Delivery requestB);
}
