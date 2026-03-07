package com.ragebait.listeners;

import com.ragebait.StatusTrackerManager;
import net.dv8tion.jda.api.events.user.update.UserUpdateActivitiesEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class PresenceListener extends ListenerAdapter {

    @Override
    public void onUserUpdateActivities(UserUpdateActivitiesEvent event) {
        StatusTrackerManager st = StatusTrackerManager.getInstance();
        
        // Ne traiter que si le tracker est activé et que c'est la cible
        if (!st.isEnabled() || st.getTargetUserId() == null) {
            return;
        }
        
        if (event.getMember() != null && event.getMember().getIdLong() == st.getTargetUserId()) {
            st.onPresenceUpdate(event.getMember());
        }
    }
}
