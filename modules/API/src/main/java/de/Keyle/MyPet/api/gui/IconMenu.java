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

package de.Keyle.MyPet.api.gui;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.util.locale.Translation;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconMenu implements Listener {

    private IconMenuInventory inventory;
    private String title;

    private @Nullable String paginationBasePath;
    private @Nullable Integer pageSizeInSlots;
    private int currentPageIndex;

    private OptionClickEventHandler handler;

    protected Map<Integer, IconMenuItem> options = new HashMap<>(54);
    private int maximumOptionPosition;
    private int nextVacantOptionPosition;

    private final Plugin plugin;

    public IconMenu(String title, OptionClickEventHandler handler, Plugin plugin) {
        this.title = title;
        this.handler = handler;
        this.plugin = plugin;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getSize() {
        if (pageSizeInSlots != null)
            return pageSizeInSlots;

        int size = maximumOptionPosition + 1;
        return (int) (Math.ceil(size / 9.) * 9);
    }

    public IconMenu setPaginationIdentifier(String identifier) {
        this.paginationBasePath = "MyPet.Pagination." + identifier;

        int totalRows = plugin.getConfig().getInt(paginationBasePath + ".TotalRows", -1);

        if (totalRows > 0) {
            if (totalRows > 6)
                totalRows = 6;

            this.pageSizeInSlots = totalRows * 9;
        }

        return this;
    }

    private void advanceNextVacantOptionPosition() {
        do {
            ++nextVacantOptionPosition;
        } while (options.containsKey(nextVacantOptionPosition));
    }

    public void setOption(int position, IconMenuItem icon) {
        if (position < 0)
            return;

        if (position > maximumOptionPosition)
            maximumOptionPosition = position;

        if (position == nextVacantOptionPosition)
            advanceNextVacantOptionPosition();

        options.put(position, icon);
    }

    public int addOption(IconMenuItem icon) {
        int position = nextVacantOptionPosition;

        options.put(position, icon);
        advanceNextVacantOptionPosition();

        return position;
    }

    private int getNumberOfPages() {
        if (pageSizeInSlots == null)
            return 1;

        int pageCapacity = pageSizeInSlots - 9;
        return ((maximumOptionPosition + 1) + (pageCapacity - 1)) / pageCapacity;
    }

    private String substituteVariablesAndColors(String input) {
        String colorizedInput = ChatColor.translateAlternateColorCodes('&', input);

        if (pageSizeInSlots == null)
            return colorizedInput;

        return colorizedInput
          .replace("{currentPage}", String.valueOf(currentPageIndex + 1))
          .replace("{numberOfPages}", String.valueOf(getNumberOfPages()));
    }

    private IconMenuItem makeConfigurableItem(String key) {
        IconMenuItem result = new IconMenuItem();

        try {
            String materialString = plugin.getConfig().getString(paginationBasePath + "." + key + ".Type");
            result.setMaterial(Material.valueOf(materialString));
        } catch (Exception ignored) {}

        try {
            String titleString = plugin.getConfig().getString(paginationBasePath + "." + key + ".Title");
            result.setTitle(substituteVariablesAndColors(titleString));
        } catch (Exception ignored) {}

        try {
            List<String> loreLines = plugin.getConfig().getStringList(paginationBasePath + "." + key + ".Lore");
            String[] loreContents = new String[loreLines.size()];

            for (int i = 0; i < loreLines.size(); ++i)
                loreContents[i] = substituteVariablesAndColors(loreLines.get(i));

            result.setLore(loreContents);
        } catch (Exception ignored) {}

        return result;
    }

    public IconMenuItem getOption(int position) {
        if (pageSizeInSlots != null) {
            if (position == pageSizeInSlots - 9)
                return makeConfigurableItem("PreviousPage");

            if (position == pageSizeInSlots - 1)
                return makeConfigurableItem("NextPage");

            // Last row is always empty, besides the navigation-buttons
            if (position > pageSizeInSlots - 9 && position < pageSizeInSlots - 1)
                return null;

            int pageCapacity = pageSizeInSlots - 9;
            int optionsOffset = currentPageIndex * pageCapacity;

            position += optionsOffset;
        }

        return options.get(position);
    }

    public void open(HumanEntity player) {
        if (player.isSleeping()) {
            player.sendMessage(Translation.getString("Message.No.CanUse", player));
            return;
        }
        if (inventory == null) {
            inventory = MyPetApi.getCompatUtil().getCompatInstance(IconMenuInventory.class, "util.iconmenu", "IconMenuInventory");
        }
        inventory.open(this, player);
    }

    public void update() {
        if (inventory != null) {
            if (getSize() != inventory.getSize()) {
                List<HumanEntity> viewers = inventory.getViewers();
                inventory.close();
                for (HumanEntity viewer : viewers) {
                    this.open(viewer);
                }
            } else {
                inventory.update(this);
            }
        }
    }

    public void destroy() {
        if (inventory != null) {
            inventory.close();
            handler = null;
            inventory = null;
        }
        HandlerList.unregisterAll(this);
    }

    private void previousPage() {
        if (currentPageIndex == 0)
            return;

        --currentPageIndex;
        update();
    }

    private void nextPage() {
        if (pageSizeInSlots == null)
            return;

        if (currentPageIndex == getNumberOfPages() - 1)
            return;

        ++currentPageIndex;
        update();
    }

    @EventHandler
    void onInventoryClose(InventoryCloseEvent event) {
        if (inventory != null && inventory.isMenuInventory(event.getInventory()) && inventory.getViewers().size() == 0) {
            inventory = null;
        }
    }

    @EventHandler
    void onPluginDisable(PluginDisableEvent event) {
        destroy();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void on(InventoryClickEvent event) {
        if (inventory != null && inventory.isMenuInventory(event.getInventory())) {
            event.setCursor(null);
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            int slot = event.getRawSlot();

            if (slot < 0 || slot >= getSize())
                return;

            int absolutePosition = slot;

            if (pageSizeInSlots != null) {
                if (slot == pageSizeInSlots - 9) {
                    previousPage();
                    return;
                }

                if (slot == pageSizeInSlots - 1) {
                    nextPage();
                    return;
                }

                int pageCapacity = pageSizeInSlots - 9;
                absolutePosition += currentPageIndex * pageCapacity;
            }

            if (options.containsKey(absolutePosition)) {
                final IconMenu.OptionClickEvent e = new IconMenu.OptionClickEvent((Player) event.getWhoClicked(), absolutePosition, this, options.get(absolutePosition));
                handler.onOptionClick(e);

                final Player p = (Player) event.getWhoClicked();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (e.willClose()) {
                            p.closeInventory();
                        }
                        if (e.willDestroy()) {
                            destroy();
                        }
                    }
                }.runTaskLater(MyPetApi.getPlugin(), 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onMonitor(InventoryClickEvent event) {
        if (inventory != null && inventory.isMenuInventory(event.getInventory())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    public interface OptionClickEventHandler {
        void onOptionClick(OptionClickEvent event);
    }

    public class OptionClickEvent {
        private Player player;
        private int position;
        private IconMenuItem option;
        private boolean close;
        private boolean destroy;
        private IconMenu menu;

        public OptionClickEvent(Player player, int position, IconMenu menu, IconMenuItem option) {
            this.player = player;
            this.position = position;
            this.menu = menu;
            this.close = true;
            this.destroy = true;
            this.option = option;
        }

        public Player getPlayer() {
            return player;
        }

        public int getPosition() {
            return position;
        }

        public IconMenuItem getOption() {
            return option;
        }

        public IconMenu getMenu() {
            return menu;
        }

        public boolean willClose() {
            return close;
        }

        public boolean willDestroy() {
            return destroy;
        }

        public void setWillClose(boolean close) {
            this.close = close;
        }

        public void setWillDestroy(boolean destroy) {
            this.destroy = destroy;
        }
    }
}