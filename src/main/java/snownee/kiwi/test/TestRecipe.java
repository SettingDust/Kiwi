package snownee.kiwi.test;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import snownee.kiwi.recipe.crafting.DynamicShapedRecipe;
import snownee.kiwi.recipe.crafting.NoContainersShapedRecipe;

public class TestRecipe extends DynamicShapedRecipe {

	public TestRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification, boolean differentInputs) {
		super(group, category, pattern, result, showNotification, differentInputs);
	}

	public TestRecipe(CraftingBookCategory category) {
		super(category);
	}

	// optional
	@Override
	public boolean matches(CraftingContainer inv, Level worldIn) {
		int[] pos = search(inv);
		if (pos == null) {
			return false;
		}
		ItemStack stack = item('#', inv, pos);
		return stack.hasTag() && stack.getTag().contains("Rarity");
	}

	@Override
	public ItemStack assemble(CraftingContainer inv, RegistryAccess registryAccess) {
		ItemStack res = result.copy();
		int[] pos = search(inv);
		ItemStack stack = item('#', inv, pos);
		if ("SSR".equals(stack.getTag().getString("Rarity"))) {
			res.grow(res.getCount());
		}
		return res;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return null; //TODO your serializer
	}

	public static class Serializer extends DynamicShapedRecipe.Serializer<TestRecipe> {

		public static final Codec<TestRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ExtraCodecs.strictOptionalField(Codec.STRING, "group", "").forGetter(DynamicShapedRecipe::getGroup),
				CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(DynamicShapedRecipe::category),
				ShapedRecipePattern.MAP_CODEC.forGetter(DynamicShapedRecipe::pattern),
				ItemStack.ITEM_WITH_COUNT_CODEC.fieldOf("result").forGetter(DynamicShapedRecipe::result),
				ExtraCodecs.strictOptionalField(Codec.BOOL, "show_notification", true).forGetter(DynamicShapedRecipe::showNotification),
				Codec.BOOL.fieldOf("different_inputs").orElse(false).forGetter(TestRecipe::allowDifferentInputs)
		).apply(instance, TestRecipe::new));

		@Override
		public Codec<TestRecipe> codec() {
			return CODEC;
		}

		@Override
		public TestRecipe fromNetwork(FriendlyByteBuf pBuffer) {
			//TODO customize recipe
			return fromNetwork(TestRecipe::new, pBuffer);
		}

	}
}
