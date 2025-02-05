package snownee.kiwi;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;
import com.google.common.collect.Sets;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import snownee.kiwi.KiwiModule.Category;
import snownee.kiwi.KiwiModule.RenderLayer;
import snownee.kiwi.block.IKiwiBlock;
import snownee.kiwi.item.ItemCategoryFiller;
import snownee.kiwi.item.ModBlockItem;
import snownee.kiwi.loader.Platform;
import snownee.kiwi.loader.event.ClientInitEvent;
import snownee.kiwi.loader.event.InitEvent;
import snownee.kiwi.loader.event.PostInitEvent;
import snownee.kiwi.loader.event.ServerInitEvent;

public class ModuleInfo {
	public static final class RegistryHolder {
		final Multimap<Registry<?>, NamedEntry<?>> registries = ListMultimapBuilder.linkedHashKeys().linkedListValues().build();

		<T> void put(NamedEntry<T> entry) {
			registries.put(entry.registry, entry);
		}

		<T> Collection<NamedEntry<T>> get(Registry<T> registry) {
			return (Collection<NamedEntry<T>>) (Object) registries.get(registry);
		}
	}

	public final AbstractModule module;
	public final ModContext context;
	public GroupSetting groupSetting;
	final RegistryHolder registries = new RegistryHolder();
	final Map<Block, Item.Properties> blockItemBuilders = Maps.newHashMap();
	final Set<Object> noCategories = Sets.newHashSet();
	final Set<Block> noItems = Sets.newHashSet();

	public ModuleInfo(ResourceLocation rl, AbstractModule module, ModContext context) {
		this.module = module;
		this.context = context;
		module.uid = rl;
		//		if (FabricDataGenHelper.ENABLED && context.modContainer instanceof FMLModContainer) {
		//			((FMLModContainer) context.modContainer).getEventBus().addListener(module::gatherData);
		//		}
	}

	/**
	 * @since 2.5.2
	 */
	@SuppressWarnings("rawtypes")
	public void register(Object object, ResourceLocation name, Registry<?> registry, @Nullable Field field) {
		NamedEntry entry = new NamedEntry(name, object, registry, field);
		registries.put(entry);
		if (field != null) {
			Category group = field.getAnnotation(Category.class);
			if (group != null) {
				entry.groupSetting = GroupSetting.of(group, groupSetting);
			}
		}
	}

	@SuppressWarnings("rawtypes")
	public <T> void handleRegister(Object registry) {
		context.setActiveContainer();
		Collection<NamedEntry<T>> entries = registries.get((Registry<T>) registry);
		BiConsumer<ModuleInfo, T> decorator = (BiConsumer<ModuleInfo, T>) module.decorators.getOrDefault(registry, (a, b) -> {
		});
		if (registry == BuiltInRegistries.ITEM) {
			registries.get(BuiltInRegistries.BLOCK).forEach(e -> {
				if (noItems.contains(e.entry))
					return;
				Item.Properties builder = blockItemBuilders.get(e.entry);
				if (builder == null)
					builder = new Item.Properties();
				BlockItem item;
				if (e.entry instanceof IKiwiBlock) {
					item = ((IKiwiBlock) e.entry).createItem(builder);
				} else {
					item = new ModBlockItem(e.entry, builder);
				}
				if (noCategories.contains(e.entry)) {
					noCategories.add(item);
				}
				NamedEntry itemEntry = new NamedEntry(e.name, item, (Registry) registry, null);
				itemEntry.groupSetting = e.groupSetting;
				entries.add(itemEntry);
			});
			entries.forEach(e -> {
				if (!noCategories.contains(e.entry)) {
					ItemCategoryFiller filler;
					if (e.entry instanceof ItemCategoryFiller) {
						filler = (ItemCategoryFiller) e.entry;
					} else {
						filler = (tab, flags, hasPermissions, items) -> items.add(new ItemStack((Item) e.entry));
					}
					if (e.groupSetting != null) {
						e.groupSetting.apply(filler);
					} else if (groupSetting != null) {
						groupSetting.apply(filler);
					}
				}
			});
		}
		entries.forEach(e -> {
			decorator.accept(this, e.entry);
			Registry.register(e.registry, e.name, e.entry);
		});
		if (registry == BuiltInRegistries.BLOCK && Platform.isPhysicalClient() && !Platform.isDataGen()) {
			final RenderType solid = RenderType.solid();
			Map<Class<?>, RenderType> cache = Maps.newHashMap();
			entries.stream().forEach(e -> {
				Block block = (Block) e.entry;
				if (e.field != null) {
					RenderLayer layer = e.field.getAnnotation(RenderLayer.class);
					if (layer != null) {
						RenderType type = (RenderType) layer.value().value;
						if (type != solid && type != null) {
							BlockRenderLayerMap.INSTANCE.putBlock(block, type);
							return;
						}
					}
				}
				Class<?> klass = block.getClass();
				RenderType type = cache.computeIfAbsent(klass, k -> {
					RenderLayer layer = null;
					while (k != Block.class) {
						layer = k.getDeclaredAnnotation(RenderLayer.class);
						if (layer != null) {
							return (RenderType) layer.value().value;
						}
						k = k.getSuperclass();
					}
					return solid;
				});
				if (type != solid && type != null) {
					BlockRenderLayerMap.INSTANCE.putBlock(block, type);
				}
			});
		}
	}

	public void preInit() {
		KiwiModules.ALL_USED_REGISTRIES.addAll(registries.registries.keySet());
		context.setActiveContainer();
		module.preInit();
		KiwiModules.ALL_USED_REGISTRIES.forEach(this::handleRegister);
	}

	public void init(InitEvent event) {
		context.setActiveContainer();
		module.init(event);
	}

	public void clientInit(ClientInitEvent event) {
		context.setActiveContainer();
		module.clientInit(event);
	}

	public void serverInit(ServerInitEvent event) {
		context.setActiveContainer();
		module.serverInit(event);
	}

	public void postInit(PostInitEvent event) {
		context.setActiveContainer();
		module.postInit(event);
	}

	public <T> List<T> getRegistries(Registry<T> registry) {
		return registries.get(registry).stream().map($ -> $.entry).toList();
	}

}
