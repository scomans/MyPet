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

package de.Keyle.MyPet.compat.v1_9_R2.entity;

import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.MyPetBukkitEntity;
import de.Keyle.MyPet.api.entity.MyPetType;
import de.Keyle.MyPet.api.entity.ai.target.TargetPriority;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.util.Compat;
import org.bukkit.craftbukkit.v1_9_R2.CraftServer;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftCreature;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_9_R2.inventory.CraftEntityEquipment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

@Compat("v1_9_R2")
public class CraftMyPet extends CraftCreature implements MyPetBukkitEntity {
    protected MyPetPlayer petOwner;
    protected EntityMyPet petEntity;
    protected CraftEntityEquipment fakeEquipment;

    public CraftMyPet(CraftServer server, EntityMyPet entityMyPet) {
        super(server, entityMyPet);
        petEntity = entityMyPet;
        fakeEquipment = new FakeEquipment(this);
    }

    public boolean canMove() {
        return petEntity.canMove();
    }

    @Override
    public void setSitting(boolean sitting) {
        getHandle().setSitting(sitting);
    }

    @Override
    public boolean isSitting() {
        return getHandle().isSitting();
    }

    @Override
    public EntityMyPet getHandle() {
        return petEntity;
    }

    public MyPet getMyPet() {
        return petEntity.getMyPet();
    }

    public MyPetPlayer getOwner() {
        if (petOwner == null) {
            petOwner = getMyPet().getOwner();
        }
        return petOwner;
    }

    @Override
    public void removeEntity() {
        getHandle().dead = true;
    }

    public MyPetType getPetType() {
        return getMyPet().getPetType();
    }

    //I saw other plugins do it this way - it should be fine and solve problems with p2 and wg
    //Update - It wasn't! - GriefPrevention didn't like the previous solution!
    //So now this ugly bs will solve it. Hopefully
    @Override
    public EntityType getType() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String class1 = stackTraceElements[2].getClassName();
        String class2 = stackTraceElements[3].getClassName();
        //all special cases here
        if(class1.contains("worldedit") || class2.contains("worldedit") ||
                class1.contains("plotsquared") || class2.contains("plotsquared") ||
                class1.contains("worldguard") || class2.contains("worldguard")  ||
                class1.contains("towny") || class2.contains("towny")) {
            return EntityType.valueOf(this.getPetType().getBukkitName());
        }

        return EntityType.UNKNOWN;
    }

    public boolean isSilent() {
        return false;
    }

    public void setSilent(boolean b) {
    }

    @Override
    public void remove() {
        // do nothing to prevent other plugins from removing the MyPet
        // user removeEntity() to remove the MyPet
    }

    @Override
    public void setHealth(double health) {
        if (health < 0) {
            health = 0;
        }
        if (health > getMaxHealth()) {
            health = getMaxHealth();
        }
        super.setHealth(health);
    }

    @Override
    public EntityEquipment getEquipment() {
        return fakeEquipment;
    }

    public void setTarget(LivingEntity target) {
        setTarget(target, TargetPriority.Bukkit);
    }

    @Override
    public void forgetTarget() {
        getHandle().forgetTarget();
    }

    public void setTarget(LivingEntity target, TargetPriority priority) {
        getHandle().setMyPetTarget(target, priority);
    }

    @Override
    public String toString() {
        return "CraftMyPet{MyPet=" + getHandle().isMyPet() + ",owner=" + getOwner() + ",type=" + getPetType() + "}";
    }

    private class FakeEquipment extends CraftEntityEquipment {

        public FakeEquipment(CraftLivingEntity entity) {
            super(entity);
        }

        @Override
        public ItemStack getItemInMainHand() {
            return null;
        }

        @Override
        public ItemStack getItemInOffHand() {
            return null;
        }

        @Override
        public ItemStack getItemInHand() {
            return null;
        }

        @Override
        public ItemStack getHelmet() {
            return null;
        }

        @Override
        public ItemStack getChestplate() {
            return null;
        }

        @Override
        public ItemStack getLeggings() {
            return null;
        }

        @Override
        public ItemStack getBoots() {
            return null;
        }

        @Override
        public ItemStack[] getArmorContents() {
            return new ItemStack[]{null, null, null, null};
        }
    }
}