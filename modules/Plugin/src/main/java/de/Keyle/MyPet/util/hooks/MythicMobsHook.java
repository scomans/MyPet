/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2019 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.util.hooks;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.entity.leashing.LeashFlag;
import de.Keyle.MyPet.api.entity.leashing.LeashFlagName;
import de.Keyle.MyPet.api.entity.leashing.LeashFlagSetting;
import de.Keyle.MyPet.api.entity.leashing.LeashFlagSettings;
import de.Keyle.MyPet.api.event.MyPetDamageEvent;
import de.Keyle.MyPet.api.skill.experience.MonsterExperience;
import de.Keyle.MyPet.api.util.hooks.PluginHookName;
import de.Keyle.MyPet.api.util.hooks.types.LeashHook;
import de.Keyle.MyPet.api.util.hooks.types.MonsterExperienceHook;
import de.Keyle.MyPet.api.util.hooks.types.PlayerVersusEntityHook;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import io.sentry.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;

@PluginHookName("MythicMobs")
public class MythicMobsHook implements LeashHook, PlayerVersusEntityHook, MonsterExperienceHook {

    public static boolean DISABLE_MYTHIC_MOB_LEASHING = true;
    
    @Override
    public boolean onEnable() {
        MyPetApi.getLeashFlagManager().registerLeashFlag(new MythicMobFlag());
        Bukkit.getPluginManager().registerEvents(this, MyPetApi.getPlugin());
        return true;
    }

    @Override
    public void onDisable() {
        MyPetApi.getLeashFlagManager().removeFlag("MythicMobs");
        HandlerList.unregisterAll(this);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        config.addDefault("Disable-Leashing", DISABLE_MYTHIC_MOB_LEASHING);

        DISABLE_MYTHIC_MOB_LEASHING = config.getBoolean("Disable-Leashing", true);
    }

    @EventHandler
    public void on(MyPetDamageEvent event) {
        try {
            if (MythicMobs.inst().getMobManager().isActiveMob(BukkitAdapter.adapt(event.getTarget()))) {
                ActiveMob defender = MythicMobs.inst().getMobManager().getMythicMobInstance(event.getTarget());
                MythicMob defenderType = defender.getType();

                if (defenderType.getIsInvincible()) {
                    event.setCancelled(true);
                    return;
                }

                double damage, baseDamage = damage = event.getDamage();
                damage -= defender.getArmor();
                if (baseDamage >= 1D && damage < 1D) {
                    damage = 1D;
                }
                for (String m : defenderType.getDamageModifiers()) {
                    if (m.startsWith("ENTITY_ATTACK")) {
                        damage *= Util.parseDouble(m.substring(14), 1D);
                        break;
                    }
                }
                event.setDamage(damage);
                if (damage == 0) {
                    event.setCancelled(true);
                }
            }

        } catch (NumberFormatException ignored) {
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public boolean canLeash(Player attacker, Entity defender) {
        if (DISABLE_MYTHIC_MOB_LEASHING) {
            try {
                if (MythicMobs.inst().getMobManager().isActiveMob(BukkitAdapter.adapt(defender))) {
                    MythicMob defenderType = MythicMobs.inst().getMobManager().getMythicMobInstance(defender).getType();
                    for (MythicMob m : MythicMobs.inst().getMobManager().getVanillaTypes()) {
                        if (m.equals(defenderType)) {
                            return true;
                        }
                    }
                    return false;
                }
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    @Override
    public boolean canHurt(Player attacker, Entity defender) {
        try {
            if (MythicMobs.inst().getMobManager().isActiveMob(BukkitAdapter.adapt(defender))) {
                MythicMob defenderType = MythicMobs.inst().getMobManager().getMythicMobInstance(defender).getType();
                if (defenderType.getIsInvincible()) {
                    return false;
                }
                for (String m : defenderType.getDamageModifiers()) {
                    if (m.startsWith("ENTITY_ATTACK")) {
                        double modifier = Util.parseDouble(m.substring(14), 1D);
                        if (modifier == 0) {
                            return false;
                        }
                        break;
                    }
                }
            }
        } catch (NumberFormatException ignored) {
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return true;
    }

    @Override
    public MonsterExperience getMonsterExperience(Entity entity) {
        try {
            if (MythicMobs.inst().getMobManager().isActiveMob(BukkitAdapter.adapt(entity))) {
                MythicMob defenderType = MythicMobs.inst().getMobManager().getMythicMobInstance(entity).getType();
                return MonsterExperience.CUSTOM_MOB_EXP.get("[MythicMobs]=" + defenderType.getInternalName());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    @LeashFlagName("MythicMobs")
    class MythicMobFlag implements LeashFlag {

        @Override
        public boolean check(Player player, LivingEntity entity, double damage, LeashFlagSettings settings) {
            if (MythicMobs.inst().getMobManager().isActiveMob(BukkitAdapter.adapt(entity))) {
                String name = MythicMobs.inst().getMobManager().getMythicMobInstance(entity).getType().getInternalName();
                for (LeashFlagSetting setting : settings.all()) {
                    if (setting.getValue().equalsIgnoreCase(name)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
    }
}