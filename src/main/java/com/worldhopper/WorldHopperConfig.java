package com.worldhopper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("randomworldhopperpp")
public interface WorldHopperConfig extends Config
{
	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "cooldownMinutes",
		name = "World Cooldown",
		description = "Minutes a world stays on cooldown after you hop away from it. Also editable"
			+ " directly from the sidebar panel.",
		position = 0
	)
	default int cooldownMinutes()
	{
		return 5;
	}

	@Range(min = 0, max = 400)
	@ConfigItem(
		keyName = "pingThreshold",
		name = "Max ping for hop (ms)",
		description = "The random-hop button only considers worlds with a ping at or below this value.",
		position = 1
	)
	default int pingThreshold()
	{
		return 150;
	}

	@ConfigSection(
		name = "World types",
		description = "Which world types the random-hop button is allowed to pick from.",
		position = 2
	)
	String worldTypesSection = "worldTypesSection";

	@ConfigItem(
		keyName = "allowNormalWorlds",
		name = "Normal worlds",
		description = "Standard worlds, including minigame worlds. Excludes Deadman, Skill Total, and Last Man Standing.",
		position = 0,
		section = worldTypesSection
	)
	default boolean allowNormalWorlds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "allowDeadmanWorlds",
		name = "Deadman worlds",
		description = "Allow hopping to Deadman mode worlds.",
		position = 1,
		section = worldTypesSection
	)
	default boolean allowDeadmanWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "allowSeasonalWorlds",
		name = "Seasonal/Tournament worlds",
		description = "Allow hopping to seasonal (e.g. Leagues) or tournament worlds.",
		position = 2,
		section = worldTypesSection
	)
	default boolean allowSeasonalWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "allowQuestSpeedrunningWorlds",
		name = "Quest speedrunning worlds",
		description = "Allow hopping to quest speedrunning worlds.",
		position = 3,
		section = worldTypesSection
	)
	default boolean allowQuestSpeedrunningWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "allowFreshStartWorlds",
		name = "Fresh start worlds",
		description = "Allow hopping to Fresh Start worlds.",
		position = 4,
		section = worldTypesSection
	)
	default boolean allowFreshStartWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "allowPvpWorlds",
		name = "PVP worlds",
		description = "Allow hopping to PVP worlds.",
		position = 5,
		section = worldTypesSection
	)
	default boolean allowPvpWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "allowSkillTotalWorlds",
		name = "Skill total worlds",
		description = "Allow hopping to worlds with a minimum total level requirement - only worlds your"
			+ " account's total level actually qualifies for are considered.",
		position = 6,
		section = worldTypesSection
	)
	default boolean allowSkillTotalWorlds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "allowHighRiskWorlds",
		name = "High risk worlds",
		description = "Allow hopping to High Risk worlds.",
		position = 7,
		section = worldTypesSection
	)
	default boolean allowHighRiskWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "allowBountyHunterWorlds",
		name = "Bounty Hunter worlds",
		description = "Allow hopping to Bounty Hunter worlds.",
		position = 8,
		section = worldTypesSection
	)
	default boolean allowBountyHunterWorlds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "allowF2pWorlds",
		name = "Allow F2P worlds",
		description = "If unchecked, only members worlds are considered for the random hop.",
		position = 9,
		section = worldTypesSection
	)
	default boolean allowF2pWorlds()
	{
		return false;
	}
}
