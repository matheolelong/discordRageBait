package com.ragebait;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VoiceRewardManager {
    private static final Logger log = LoggerFactory.getLogger(VoiceRewardManager.class);
    private static VoiceRewardManager instance;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private JDA jda;
    private boolean running = false;

    private VoiceRewardManager() {}

    public static synchronized VoiceRewardManager getInstance() {
        if (instance == null) {
            instance = new VoiceRewardManager();
        }
        return instance;
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    public void start() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::rewardVoiceUsers, 0, 1, TimeUnit.MINUTES);
        log.info("[VoiceReward] Récompense vocale activée (10 coins/minute)");
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        log.info("[VoiceReward] Arrêtée.");
    }

    private void rewardVoiceUsers() {
        if (jda == null) return;
        int rewarded = 0;
        for (Guild guild : jda.getGuilds()) {
            List<VoiceChannel> voiceChannels = guild.getVoiceChannels();
            for (VoiceChannel channel : voiceChannels) {
                for (Member member : channel.getMembers()) {
                    if (member.getUser().isBot()) continue;
                    CasinoManager.getInstance().addBalance(member.getIdLong(), 10);
                    rewarded++;
                }
            }
        }
        if (rewarded > 0) log.info("[VoiceReward] {} membres récompensés.", rewarded);
    }
}
