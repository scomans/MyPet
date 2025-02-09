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

package de.Keyle.MyPet.gui.selectionmenu;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.WorldGroup;
import de.Keyle.MyPet.api.entity.StoredMyPet;
import de.Keyle.MyPet.api.gui.IconMenu;
import de.Keyle.MyPet.api.gui.IconMenuItem;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.repository.RepositoryCallback;
import de.Keyle.MyPet.api.util.Colorizer;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.Keyle.MyPet.api.util.service.types.EggIconService;
import de.keyle.knbt.TagCompound;
import de.keyle.knbt.TagInt;

import java.util.*;

import static org.bukkit.ChatColor.GOLD;
import static org.bukkit.ChatColor.RESET;

public class MyPetSelectionGui {

    private final MyPetPlayer player;
    private final String title;

    public MyPetSelectionGui(MyPetPlayer player, String title) {
        this.player = player;
        this.title = title;
    }

    public void open(List<StoredMyPet> pets, final RepositoryCallback<StoredMyPet> callback) {
        final Map<Integer, StoredMyPet> petSlotList = new HashMap<>();
        WorldGroup wg = WorldGroup.getGroupByWorld(player.getPlayer().getWorld().getName());

        IconMenu menu = new IconMenu(title, event -> {
            if (petSlotList.containsKey(event.getPosition())) {
                StoredMyPet storedMyPet = petSlotList.get(event.getPosition());
                if (storedMyPet != null && callback != null) {
                    callback.callback(storedMyPet);
                }
            }

            event.setWillClose(true);
            event.setWillDestroy(true);
        }, MyPetApi.getPlugin()).setPaginationIdentifier("SelectMyPet");

        int nextPosition = 0;

        for (StoredMyPet currentPet : pets) {
            if (currentPet.getWorldGroup().isEmpty() || !currentPet.getWorldGroup().equals(wg.getName()))
                continue;

            if (player.hasMyPet() && player.getMyPet().getUUID().equals(currentPet.getUUID()))
                continue;

            List<String> lore = new ArrayList<>();

            if (Configuration.HungerSystem.USE_HUNGER_SYSTEM)
                lore.add(RESET + Translation.getString("Name.Hunger", player) + ": " + GOLD + Math.round(currentPet.getSaturation()));

            if (currentPet.getRespawnTime() > 0) {
                lore.add(RESET + Translation.getString("Name.Respawntime", player) + ": " + GOLD + currentPet.getRespawnTime() + "sec");
            } else {
                lore.add(RESET + Translation.getString("Name.HP", player) + ": " + GOLD + String.format("%1.2f", currentPet.getHealth()));
            }

            boolean levelFound = false;
            if (currentPet.getInfo().containsKey("storage")) {
                TagCompound storage = currentPet.getInfo().getAs("storage", TagCompound.class);
                if (storage.containsKey("level")) {
                    lore.add(RESET + Translation.getString("Name.Level", player) + ": " + GOLD + storage.getAs("level", TagInt.class).getIntData());
                    levelFound = true;
                }
            }

            if (!levelFound)
                lore.add(RESET + Translation.getString("Name.Exp", player) + ": " + GOLD + String.format("%1.2f", currentPet.getExp()));

            lore.add(RESET + Translation.getString("Name.Type", player) + ": " + GOLD + Translation.getString("Name." + currentPet.getPetType().name(), player));
            lore.add(RESET + Translation.getString("Name.Skilltree", player) + ": " + GOLD + Colorizer.setColors(currentPet.getSkilltree() != null ? currentPet.getSkilltree().getDisplayName() : "-"));

            IconMenuItem icon = new IconMenuItem();
            icon.setTitle(RESET + currentPet.getPetName());
            icon.addLore(lore);
            Optional<EggIconService> egg = MyPetApi.getServiceManager().getService(EggIconService.class);
            egg.ifPresent(service -> service.updateIcon(currentPet.getPetType(), icon));

            int currentPosition = nextPosition++;

            menu.setOption(currentPosition, icon);
            petSlotList.put(currentPosition, currentPet);
        }

        menu.open(player.getPlayer());
    }
}