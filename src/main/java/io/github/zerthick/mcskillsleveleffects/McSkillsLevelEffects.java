/*
 * Copyright (C) 2018  Zerthick
 *
 * This file is part of mcSkills-LevelEffects.
 *
 * mcSkills-LevelEffects is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * mcSkills-LevelEffects is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with mcSkills-LevelEffects.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.zerthick.mcskillsleveleffects;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.github.zerthick.mcskills.api.account.McSkillsAccount;
import io.github.zerthick.mcskills.api.event.experience.McSkillsChangeExperienceEvent;
import io.github.zerthick.mcskills.api.event.experience.McSkillsChangeLevelEvent;
import io.github.zerthick.mcskills.api.event.experience.McSkillsEventContextKeys;
import io.github.zerthick.mcskills.api.experience.McSkillsExperienceService;
import io.github.zerthick.mcskills.api.skill.McSkillsSkill;
import io.github.zerthick.mcskills.api.skill.McSkillsSkillService;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.boss.BossBarColors;
import org.spongepowered.api.boss.BossBarOverlays;
import org.spongepowered.api.boss.ServerBossBar;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.projectile.Firework;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.item.FireworkEffect;
import org.spongepowered.api.item.FireworkShapes;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "mcskillsleveleffects",
        name = "McSkills-LevelEffects",
        authors = {
                "Zerthick"
        },
        dependencies = {
                @Dependency(id = "mcskills")
        }
)
public class McSkillsLevelEffects {

    private Map<UUID, ServerBossBar> bossBars;
    private Map<UUID, Instant> eventTimestamps;

    private boolean bossBarEnabled;
    private boolean levelUpSoundEnabled;
    private int fireworkInterval;

    @Inject
    private Logger logger;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private Path defaultConfig;

    @Inject
    @ConfigDir(sharedRoot = true)
    private Path defaultConfigDir;

    @Inject
    private PluginContainer instance;

    @Listener
    public void onGameInit(GameInitializationEvent event) {

        ConfigurationLoader<CommentedConfigurationNode> configLoader = HoconConfigurationLoader.builder().setPath(defaultConfig).build();

        //Generate default config if it doesn't exist
        if (!defaultConfig.toFile().exists()) {
            Asset defaultConfigAsset = instance.getAsset("mcskillsleveleffects.conf").get();
            try {
                defaultConfigAsset.copyToFile(defaultConfig);
                configLoader.save(configLoader.load());
            } catch (IOException e) {
                logger.warn("Error loading default config! Error: " + e.getMessage());
            }
        }

        try {

            CommentedConfigurationNode bossBarNode = configLoader.load().getNode("bossbar");
            bossBarEnabled = bossBarNode.getBoolean(true);
            CommentedConfigurationNode levelUpSoundNode = configLoader.load().getNode("levelUpSound");
            levelUpSoundEnabled = levelUpSoundNode.getBoolean(true);
            CommentedConfigurationNode fireworkIntervalNode = configLoader.load().getNode("fireworkInterval");
            fireworkInterval = fireworkIntervalNode.getInt(10);

        } catch (IOException e) {
            logger.warn("Error loading config! Error: " + e.getMessage());
        }

        if (bossBarEnabled) {

            bossBars = new HashMap<>();
            eventTimestamps = new HashMap<>();


            Task.builder()
                    .execute(() -> eventTimestamps.forEach((k, v) -> {
                        if (Duration.between(v, Instant.now()).compareTo(Duration.ofSeconds(10)) > 0) {
                            bossBars.get(k).setVisible(false);
                        }
                    }))
                    .interval(5, TimeUnit.SECONDS)
                    .name("McSkills-LevelEffects BossBar Clear Task")
                    .submit(this);
        }
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {

        // Log Start Up to Console
        logger.info(
                instance.getName() + " version " + instance.getVersion().orElse("unknown")
                        + " enabled!");
    }

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event, @Getter("getTargetEntity") Player player) {

        if (bossBarEnabled) {

            UUID playerUniqueIdentifier = player.getUniqueId();

            ServerBossBar bossBar = ServerBossBar.builder()
                    .name(Text.EMPTY)
                    .color(BossBarColors.WHITE)
                    .overlay(BossBarOverlays.PROGRESS)
                    .createFog(false)
                    .darkenSky(false)
                    .playEndBossMusic(false)
                    .visible(false)
                    .build();
            bossBar.addPlayer(player);
            bossBars.put(playerUniqueIdentifier, bossBar);
        }
    }

    @Listener
    public void onPlayerLeave(ClientConnectionEvent.Disconnect event, @Getter("getTargetEntity") Player player) {

        UUID playerUniqueIdentifier = player.getUniqueId();

        bossBars.remove(playerUniqueIdentifier);
        eventTimestamps.remove(playerUniqueIdentifier);
    }

    @Listener(order = Order.LAST)
    public void onChangeMcSkillsExperience(McSkillsChangeExperienceEvent event) {

        if (bossBarEnabled) {

            McSkillsSkillService skillsService = Sponge.getServiceManager().provideUnchecked(McSkillsSkillService.class);
            McSkillsExperienceService experienceService = Sponge.getServiceManager().provideUnchecked(McSkillsExperienceService.class);

            Optional<McSkillsAccount> accountOptional = event.getContext().get(McSkillsEventContextKeys.MCSKILLS_ACCOUNT);
            Optional<String> skillIdOptional = event.getContext().get(McSkillsEventContextKeys.MCSKILLS_SKILL_ID);

            if (accountOptional.isPresent() && skillIdOptional.isPresent()) {

                McSkillsAccount account = accountOptional.get();
                String skillId = skillIdOptional.get();

                McSkillsSkill skill = skillsService.getSkill(skillId).get();
                int skillLevel = account.getSkillLevel(skillId);
                long skillExperience = account.getSkillExperience(skillId) + event.getExperience();

                float percent = (float) skillExperience / experienceService.getLevelExperience(skillLevel);
                if (percent > 1) {
                    percent = 1;
                }

                ServerBossBar bossBar = bossBars.get(event.getTargetEntity().getUniqueId());

                bossBar.setName(skill.getSkillName());
                bossBar.setPercent(percent);
                bossBar.setVisible(true);

                eventTimestamps.put(event.getTargetEntity().getUniqueId(), Instant.now());
            }
        }
    }

    @Listener(order = Order.LAST)
    public void onChangeMcSkillsLevel(McSkillsChangeLevelEvent event) {

        Player player = event.getTargetEntity();

        McSkillsSkillService skillsService = Sponge.getServiceManager().provideUnchecked(McSkillsSkillService.class);
        McSkillsExperienceService experienceService = Sponge.getServiceManager().provideUnchecked(McSkillsExperienceService.class);

        Optional<String> skillIdOptional = event.getContext().get(McSkillsEventContextKeys.MCSKILLS_SKILL_ID);

        if (skillIdOptional.isPresent()) {

            String skillId = skillIdOptional.get();

            McSkillsSkill skill = skillsService.getSkill(skillId).get();

            if (bossBarEnabled) {

                int skillLevel = event.getLevel();
                long skillExperience = event.getRemainingExperience();

                float percent = (float) skillExperience / experienceService.getLevelExperience(skillLevel);
                if (percent > 1) {
                    percent = 1;
                }

                ServerBossBar bossBar = bossBars.get(player.getUniqueId());

                bossBar.setName(skill.getSkillName());
                bossBar.setPercent(percent);
                bossBar.setVisible(true);

                eventTimestamps.put(player.getUniqueId(), Instant.now());
            }

            if (event instanceof McSkillsChangeLevelEvent.Up) {

                if (levelUpSoundEnabled) {
                    player.playSound(SoundTypes.ENTITY_PLAYER_LEVELUP, player.getLocation().getPosition(), 1);
                }

                if (fireworkInterval != 0 && event.getLevel() % fireworkInterval == 0) {

                    player.launchProjectile(Firework.class).ifPresent(firework -> {
                        FireworkEffect effect = FireworkEffect.builder()
                                .color(skill.getSkillName().getColor().getColor())
                                .shape(FireworkShapes.BALL)
                                .trail(true)
                                .build();

                        firework.offer(Keys.FIREWORK_EFFECTS, ImmutableList.of(effect));
                    });
                }
            }
        }
    }
}
