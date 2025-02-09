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

package de.Keyle.MyPet.util.shop;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.WorldGroup;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.StoredMyPet;
import de.Keyle.MyPet.api.event.MyPetCreateEvent;
import de.Keyle.MyPet.api.exceptions.MyPetTypeNotFoundException;
import de.Keyle.MyPet.api.gui.IconMenu;
import de.Keyle.MyPet.api.gui.IconMenuItem;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.player.Permissions;
import de.Keyle.MyPet.api.repository.RepositoryCallback;
import de.Keyle.MyPet.api.skill.skilltree.SkilltreeIcon;
import de.Keyle.MyPet.api.util.Colorizer;
import de.Keyle.MyPet.api.util.EnumSelector;
import de.Keyle.MyPet.api.util.WalletType;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.Keyle.MyPet.api.util.service.types.RepositoryMyPetConverterService;
import de.Keyle.MyPet.util.hooks.VaultHook;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import static de.Keyle.MyPet.api.util.configuration.Try.tryToLoad;

public class PetShop {

    protected String name;
    protected String displayName = "Pet - Shop";
    protected Map<Integer, ShopMyPet> pets = new HashMap<>();
    protected WalletType wallet = WalletType.None;
    @Getter @Setter protected int position = -1;
    @Getter @Setter protected SkilltreeIcon icon = new SkilltreeIcon().setMaterial("chest");
    protected String walletOwner = null;
    protected boolean defaultShop = false;
    protected double privateWallet = 0;

    public PetShop(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void open(final Player player) {
        if (!MyPetApi.getHookHelper().isEconomyEnabled()) {
            player.sendMessage(Translation.getString("Message.No.Economy", player));
            return;
        }
        VaultHook economyHook = (VaultHook) MyPetApi.getHookHelper().getEconomy();

        IconMenu shop = new IconMenu(Colorizer.setColors(displayName), event -> {
            if (pets.containsKey(event.getPosition())) {
                final ShopMyPet pet = pets.get(event.getPosition());
                if (pet != null) {
                    final Player p = event.getPlayer();
                    final MyPetPlayer owner;
                    if (MyPetApi.getPlayerManager().isMyPetPlayer(p)) {
                        owner = MyPetApi.getPlayerManager().getMyPetPlayer(player);

                        if (owner.hasMyPet() && !Permissions.has(owner, "MyPet.shop.storage")) {
                            p.sendMessage(Translation.getString("Message.Command.Trade.Receiver.HasPet", player));
                            return;
                        }
                    } else {
                        owner = null;
                    }

                    final BukkitRunnable confirmRunner = new BukkitRunnable() {
                        @Override
                        public void run() {
                            IconMenu menu = new IconMenu(Util.formatText(Translation.getString("Message.Shop.Confirm.Title", player), pet.getPetName(), economyHook.getEconomy().format(pet.getPrice())), event1 -> {
                                if (event1.getPosition() == 3) {
                                    if (pet.getPrice() > 0) {
                                        if (economyHook.canPay(p.getUniqueId(), pet.getPrice())) {
                                            if (economyHook.getEconomy().withdrawPlayer(p, pet.getPrice()).transactionSuccess()) {
                                                switch (wallet) {
                                                    case Bank:
                                                        economyHook.getEconomy().bankDeposit(walletOwner, pet.getPrice());
                                                        break;
                                                    case Player:
                                                        economyHook.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(UUID.fromString(walletOwner)), pet.getPrice());
                                                    case Private:
                                                        depositPrivate(pet.getPrice());
                                                        break;
                                                }
                                            } else {
                                                p.sendMessage(Translation.getString("Message.No.Money", player));
                                                return;
                                            }
                                        } else {
                                            p.sendMessage(Translation.getString("Message.Shop.NoMoney", player));
                                            return;
                                        }
                                    }

                                    final MyPetPlayer petOwner;
                                    if (owner == null) {
                                        petOwner = MyPetApi.getPlayerManager().registerMyPetPlayer(player);
                                    } else {
                                        petOwner = owner;
                                    }

                                    pet.setOwner(petOwner);
                                    final StoredMyPet clonedPet = MyPetApi.getMyPetManager().getInactiveMyPetFromMyPet(pet);

                                    clonedPet.setOwner(petOwner);
                                    clonedPet.setWorldGroup(WorldGroup.getGroupByWorld(player.getWorld().getName()).getName());
                                    clonedPet.setUUID(null);

                                    MyPetApi.getRepository().addMyPet(clonedPet, new RepositoryCallback<Boolean>() {
                                        @Override
                                        public void callback(Boolean value) {
                                            p.sendMessage(Util.formatText(Translation.getString("Message.Shop.Success", player), clonedPet.getPetName(), economyHook.getEconomy().format(pet.getPrice())));
                                            MyPetCreateEvent createEvent = new MyPetCreateEvent(clonedPet, MyPetCreateEvent.Source.PetShop);
                                            Bukkit.getServer().getPluginManager().callEvent(createEvent);
                                            if (petOwner.hasMyPet()) {
                                                p.sendMessage(Util.formatText(Translation.getString("Message.Shop.SuccessStorage", player), clonedPet.getPetName()));
                                            } else {
                                                petOwner.setMyPetForWorldGroup(WorldGroup.getGroupByWorld(player.getWorld().getName()), clonedPet.getUUID());
                                                MyPetApi.getRepository().updateMyPetPlayer(petOwner, null);
                                                MyPet activePet = MyPetApi.getMyPetManager().activateMyPet(clonedPet).get();
                                                activePet.createEntity();
                                            }
                                        }
                                    });
                                }
                                event1.setWillClose(true);
                                event1.setWillDestroy(true);
                            }, MyPetApi.getPlugin());
                            IconMenuItem icon = new IconMenuItem()
                                    .setMaterial(EnumSelector.find(Material.class, "WOOL", "LIME_WOOL"))
                                    .setData(5)
                                    .setTitle(ChatColor.GREEN + Translation.getString("Name.Yes", player))
                                    .setLore(ChatColor.RESET + Util.formatText(Translation.getString("Message.Shop.Confirm.Yes", player), pet.getPetName(), economyHook.getEconomy().format(pet.getPrice())));
                            if (owner != null && owner.hasMyPet()) {
                                icon.addLoreLine("").addLoreLine(Translation.getString("Message.Shop.Confirm.SendStorage", player));
                            }
                            menu.setOption(3, icon);
                            menu.setOption(5, new IconMenuItem()
                                    .setMaterial(EnumSelector.find(Material.class, "WOOL", "RED_WOOL"))
                                    .setData(14)
                                    .setTitle(ChatColor.RED + Translation.getString("Name.No", player))
                                    .setLore(ChatColor.RESET + Util.formatText(Translation.getString("Message.Shop.Confirm.No", player), pet.getPetName(), economyHook.getEconomy().format(pet.getPrice()))));
                            menu.open(player);
                        }
                    };

                    if (owner != null && owner.hasMyPet()) {
                        MyPetApi.getRepository().getMyPets(owner, new RepositoryCallback<List<StoredMyPet>>() {
                            @Override
                            public void callback(List<StoredMyPet> value) {
                                int petCount = getInactivePetCount(value, WorldGroup.getGroupByWorld(player.getWorld().getName()).getName()) - 1;
                                int limit = getMaxPetCount(p);
                                if (petCount >= limit) {
                                    p.sendMessage(Util.formatText(Translation.getString("Message.Command.Switch.Limit", player), limit));
                                    return;
                                }
                                confirmRunner.runTaskLater(MyPetApi.getPlugin(), 5L);
                            }
                        });
                    } else {
                        confirmRunner.runTaskLater(MyPetApi.getPlugin(), 5L);
                    }
                }
            }
        }, MyPetApi.getPlugin()).setPaginationIdentifier("PetShop");

        double balance = economyHook.getBalance(player);
        for (int pos : pets.keySet()) {
            ShopMyPet pet = pets.get(pos);
            IconMenuItem icon = pet.getIcon();
            ChatColor canPay = balance >= pet.getPrice() ? ChatColor.GREEN : ChatColor.RED;
            icon.addLoreLine(ChatColor.RESET + "" + ChatColor.BLUE + Translation.getString("Name.Price", player) + ": " + canPay + economyHook.getEconomy().format(pet.getPrice()), 0);
            shop.setOption(pos, icon);
        }

        shop.open(player);
    }

    public int getMaxPetCount(Player p) {
        int maxPetCount = 0;
        if (Permissions.has(p, "MyPet.admin")) {
            maxPetCount = Configuration.Misc.MAX_STORED_PET_COUNT;
        } else {
            for (int i = Configuration.Misc.MAX_STORED_PET_COUNT; i > 0; i--) {
                if (Permissions.has(p, "MyPet.petstorage.limit." + i)) {
                    maxPetCount = i;
                    break;
                }
            }
        }
        return maxPetCount;
    }

    private int getInactivePetCount(List<StoredMyPet> pets, String worldGroup) {
        int inactivePetCount = 0;

        for (StoredMyPet pet : pets) {
            if (!pet.getWorldGroup().equals(worldGroup)) {
                continue;
            }
            inactivePetCount++;
        }

        return inactivePetCount;
    }

    public void depositPrivate(double amount) {
        privateWallet += amount;
    }

    public boolean isDefault() {
        return defaultShop;
    }

    public void load(ConfigurationSection section) {
        tryToLoad("Name", () -> displayName = section.getString("Name", name));
        tryToLoad("Default", () -> defaultShop = section.getBoolean("Default", false));
        tryToLoad("Position", () -> position = section.getInt("Position", -1));

        tryToLoad("Icon", () -> {
            if (section.contains("Icon")) {
                ConfigurationSection iconSection = section.getConfigurationSection("Icon");
                SkilltreeIcon icon = new SkilltreeIcon();
                tryToLoad("Icon.Material", () -> {
                    if (iconSection.contains("Material")) {
                        icon.setMaterial(iconSection.getString("Material", "chest"));
                    }
                });
                tryToLoad("Icon.Glowing", () -> {
                    if (iconSection.contains("Glowing")) {
                        icon.setGlowing(iconSection.getBoolean("Glowing", false));
                    }
                });
                this.icon = icon;
            }
        });


        tryToLoad("Balance.Type", () -> {
            wallet = WalletType.getByName(section.getString("Balance.Type", ""));
            if (wallet == null) {
                wallet = WalletType.None;
            }
            switch (wallet) {
                case Bank:
                case Player:
                    tryToLoad("Display Name", () -> {
                        walletOwner = section.getString("Balance.Owner", null);
                    });
            }
        });

        tryToLoad("Pets", () -> {
            ConfigurationSection pets = section.getConfigurationSection("Pets");
            if (pets == null) {
                MyPetApi.getLogger().warning(displayName + " shop failed to load! Please check your shop config.");
                return;
            }

            Queue<ShopMyPet> filler = new ArrayDeque<>();
            for (String name : pets.getKeys(false)) {
                tryToLoad("Pets." + name, () -> {
                    ShopMyPet pet = new ShopMyPet(name);
                    try {
                        pet.load(pets.getConfigurationSection(name));

                        List<RepositoryMyPetConverterService> converters = MyPetApi.getServiceManager().getServices(RepositoryMyPetConverterService.class);
                        for (RepositoryMyPetConverterService converter : converters) {
                            converter.convert(pet);
                        }

                        if (pet.getPosition() < 0) {
                            filler.add(pet);
                            return;
                        }

                        this.pets.put(pet.getPosition(), pet);
                    } catch (MyPetTypeNotFoundException ignored) {
                    }
                });
            }

            int slot = 0;
            while (!filler.isEmpty()) {
                if (this.pets.containsKey(slot)) {
                    slot++;
                    continue;
                }
                ShopMyPet pet = filler.poll();
                this.pets.put(slot, pet);
            }
        });
    }
}