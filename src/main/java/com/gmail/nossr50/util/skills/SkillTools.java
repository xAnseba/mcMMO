package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.api.exceptions.InvalidSkillException;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.text.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SkillTools {
    private final mcMMO pluginRef;

    //TODO: Should these be hash sets instead of lists?
    //TODO: Figure out which ones we don't need, this was copy pasted from a diff branch
    public final ImmutableList<String> LOCALIZED_SKILL_NAMES;
    public final ImmutableList<String> FORMATTED_SUBSKILL_NAMES;
    public final ImmutableSet<String> EXACT_SUBSKILL_NAMES;
    public final List<PrimarySkillType> CHILD_SKILLS;
    public final ImmutableList<PrimarySkillType> NON_CHILD_SKILLS;
    public final ImmutableList<PrimarySkillType> COMBAT_SKILLS;
    public final ImmutableList<PrimarySkillType> GATHERING_SKILLS;
    public final ImmutableList<PrimarySkillType> MISC_SKILLS;

    private EnumMap<SubSkillType, PrimarySkillType> subSkillParentRelationshipMap; //TODO: This disgusts me, but it will have to do until the new skill system is in place
    private EnumMap<SuperAbilityType, PrimarySkillType> superAbilityParentRelationshipMap; //TODO: This disgusts me, but it will have to do until the new skill system is in place
    private EnumMap<PrimarySkillType, HashSet<SubSkillType>> primarySkillChildrenMap; //TODO: This disgusts me, but it will have to do until the new skill system is in place

    // The map below is for the super abilities which require readying a tool, its everything except blast mining
    private EnumMap<PrimarySkillType, SuperAbilityType> mainActivatedAbilityChildMap; //TODO: This disgusts me, but it will have to do until the new skill system is in place
    private EnumMap<PrimarySkillType, ToolType> primarySkillToolMap; //TODO: Christ..

    public SkillTools(@NotNull mcMMO pluginRef) {
        this.pluginRef = pluginRef;

        initSubSkillRelationshipMap();
        initPrimaryChildMap();
        initPrimaryToolMap();
        initSuperAbilityParentRelationships();

        List<PrimarySkillType> childSkills = new ArrayList<>();
        List<PrimarySkillType> nonChildSkills = new ArrayList<>();

        for (PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            if (isChildSkill(primarySkillType)) {
                childSkills.add(primarySkillType);
            } else {
                nonChildSkills.add(primarySkillType);
            }
        }

        COMBAT_SKILLS = ImmutableList.of(PrimarySkillType.ARCHERY, PrimarySkillType.AXES, PrimarySkillType.SWORDS, PrimarySkillType.TAMING, PrimarySkillType.UNARMED);
        GATHERING_SKILLS = ImmutableList.of(PrimarySkillType.EXCAVATION, PrimarySkillType.FISHING, PrimarySkillType.HERBALISM, PrimarySkillType.MINING, PrimarySkillType.WOODCUTTING);
        MISC_SKILLS = ImmutableList.of(PrimarySkillType.ACROBATICS, PrimarySkillType.ALCHEMY, PrimarySkillType.REPAIR, PrimarySkillType.SALVAGE, PrimarySkillType.SMELTING);

        LOCALIZED_SKILL_NAMES = ImmutableList.copyOf(buildLocalizedPrimarySkillNames());
        FORMATTED_SUBSKILL_NAMES = ImmutableList.copyOf(buildFormattedSubSkillNameList());
        EXACT_SUBSKILL_NAMES = ImmutableSet.copyOf(buildExactSubSkillNameList());

        CHILD_SKILLS = ImmutableList.copyOf(childSkills);
        NON_CHILD_SKILLS = ImmutableList.copyOf(nonChildSkills);
    }

    //TODO: What is with this design?
    private void initPrimaryToolMap() {
        primarySkillToolMap = new EnumMap<PrimarySkillType, ToolType>(PrimarySkillType.class);

        primarySkillToolMap.put(PrimarySkillType.AXES, ToolType.AXE);
        primarySkillToolMap.put(PrimarySkillType.WOODCUTTING, ToolType.AXE);
        primarySkillToolMap.put(PrimarySkillType.UNARMED, ToolType.FISTS);
        primarySkillToolMap.put(PrimarySkillType.SWORDS, ToolType.SWORD);
        primarySkillToolMap.put(PrimarySkillType.EXCAVATION, ToolType.SHOVEL);
        primarySkillToolMap.put(PrimarySkillType.HERBALISM, ToolType.HOE);
        primarySkillToolMap.put(PrimarySkillType.MINING, ToolType.PICKAXE);
    }

    private void initSuperAbilityParentRelationships() {
        superAbilityParentRelationshipMap = new EnumMap<SuperAbilityType, PrimarySkillType>(SuperAbilityType.class);
        mainActivatedAbilityChildMap = new EnumMap<PrimarySkillType, SuperAbilityType>(PrimarySkillType.class);

        for(SuperAbilityType superAbilityType : SuperAbilityType.values()) {
            try {
                PrimarySkillType parent = getSuperAbilityParent(superAbilityType);
                superAbilityParentRelationshipMap.put(superAbilityType, parent);

                if(superAbilityType != SuperAbilityType.BLAST_MINING) {
                    //This map is used only for abilities that have a tool readying phase, so blast mining is ignored
                    mainActivatedAbilityChildMap.put(parent, superAbilityType);
                }
            } catch (InvalidSkillException e) {
                e.printStackTrace();
            }
        }
    }

    private PrimarySkillType getSuperAbilityParent(SuperAbilityType superAbilityType) throws InvalidSkillException {
        switch(superAbilityType) {
            case BERSERK:
                return PrimarySkillType.UNARMED;
            case GREEN_TERRA:
                return PrimarySkillType.HERBALISM;
            case TREE_FELLER:
                return PrimarySkillType.WOODCUTTING;
            case SUPER_BREAKER:
            case BLAST_MINING:
                return PrimarySkillType.MINING;
            case SKULL_SPLITTER:
                return PrimarySkillType.AXES;
            case SERRATED_STRIKES:
                return PrimarySkillType.SWORDS;
            case GIGA_DRILL_BREAKER:
                return PrimarySkillType.EXCAVATION;
            default:
                throw new InvalidSkillException("No parent defined for super ability! "+superAbilityType.toString());
        }
    }

    /**
     * Builds a list of localized {@link PrimarySkillType} names
     * @return list of localized {@link PrimarySkillType} names
     */
    private ArrayList<String> buildLocalizedPrimarySkillNames() {
        ArrayList<String> localizedSkillNameList = new ArrayList<>();

        for(PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            localizedSkillNameList.add(getLocalizedSkillName(primarySkillType));
        }

        Collections.sort(localizedSkillNameList);

        return localizedSkillNameList;
    }

    /**
     * Builds a map containing a HashSet of SubSkillTypes considered Children of PrimarySkillType
     * Disgusting Hacky Fix until the new skill system is in place
     */
    private void initPrimaryChildMap() {
        primarySkillChildrenMap = new EnumMap<PrimarySkillType, HashSet<SubSkillType>>(PrimarySkillType.class);

        //Init the empty Hash Sets
        for(PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            primarySkillChildrenMap.put(primarySkillType, new HashSet<SubSkillType>());
        }

        //Fill in the hash sets
        for(SubSkillType subSkillType : SubSkillType.values()) {
            PrimarySkillType parentSkill = subSkillParentRelationshipMap.get(subSkillType);

            //Add this subskill as a child
            primarySkillChildrenMap.get(parentSkill).add(subSkillType);
        }
    }

    /**
     * Makes a list of the "nice" version of sub skill names
     * Used in tab completion mostly
     * @return a list of formatted sub skill names
     */
    private ArrayList<String> buildFormattedSubSkillNameList() {
        ArrayList<String> subSkillNameList = new ArrayList<>();

        for(SubSkillType subSkillType : SubSkillType.values()) {
            subSkillNameList.add(subSkillType.getNiceNameNoSpaces(subSkillType));
        }

        return subSkillNameList;
    }

    private HashSet<String> buildExactSubSkillNameList() {
        HashSet<String> subSkillNameExactSet = new HashSet<>();

        for(SubSkillType subSkillType : SubSkillType.values()) {
            subSkillNameExactSet.add(subSkillType.toString());
        }

        return subSkillNameExactSet;
    }

    /**
     * Builds a map containing the relationships of SubSkillTypes to PrimarySkillTypes
     * Disgusting Hacky Fix until the new skill system is in place
     */
    private void initSubSkillRelationshipMap() {
        subSkillParentRelationshipMap = new EnumMap<SubSkillType, PrimarySkillType>(SubSkillType.class);

        //Super hacky and disgusting
        for(PrimarySkillType primarySkillType : PrimarySkillType.values()) {
            for(SubSkillType subSkillType : SubSkillType.values()) {
                String[] splitSubSkillName = subSkillType.toString().split("_");

                if(primarySkillType.toString().equalsIgnoreCase(splitSubSkillName[0])) {
                    //Parent Skill Found
                    subSkillParentRelationshipMap.put(subSkillType, primarySkillType);
                }
            }
        }
    }

    /**
     * Matches a string of a skill to a skill
     * This is NOT case sensitive
     * First it checks the locale file and tries to match by the localized name of the skill
     * Then if nothing is found it checks against the hard coded "name" of the skill, which is just its name in English
     *
     * @param skillName target skill name
     * @return the matching PrimarySkillType if one is found, otherwise null
     */
    public PrimarySkillType matchSkill(String skillName) {
        if (!pluginRef.getGeneralConfig().getLocale().equalsIgnoreCase("en_US")) {
            for (PrimarySkillType type : PrimarySkillType.values()) {
                if (skillName.equalsIgnoreCase(LocaleLoader.getString(StringUtils.getCapitalized(type.name()) + ".SkillName"))) {
                    return type;
                }
            }
        }

        for (PrimarySkillType type : PrimarySkillType.values()) {
            if (type.name().equalsIgnoreCase(skillName)) {
                return type;
            }
        }

        if (!skillName.equalsIgnoreCase("all")) {
            pluginRef.getLogger().warning("Invalid mcMMO skill (" + skillName + ")"); //TODO: Localize
        }

        return null;
    }

    /**
     * Gets the PrimarySkillStype to which a SubSkillType belongs
     * Return null if it does not belong to one.. which should be impossible in most circumstances
     * @param subSkillType target subskill
     * @return the PrimarySkillType of this SubSkill, null if it doesn't exist
     */
    public PrimarySkillType getPrimarySkillBySubSkill(SubSkillType subSkillType) {
        return subSkillParentRelationshipMap.get(subSkillType);
    }

    /**
     * Gets the PrimarySkillStype to which a SuperAbilityType belongs
     * Return null if it does not belong to one.. which should be impossible in most circumstances
     * @param superAbilityType target super ability
     * @return the PrimarySkillType of this SuperAbilityType, null if it doesn't exist
     */
    public PrimarySkillType getPrimarySkillBySuperAbility(SuperAbilityType superAbilityType) {
        return superAbilityParentRelationshipMap.get(superAbilityType);
    }

    public SuperAbilityType getSuperAbility(PrimarySkillType primarySkillType) {
        if(mainActivatedAbilityChildMap.get(primarySkillType) == null)
            return null;

        return mainActivatedAbilityChildMap.get(primarySkillType);
    }

    public boolean isSuperAbilityUnlocked(PrimarySkillType primarySkillType, Player player) {
        return RankUtils.getRank(player, getSuperAbility(primarySkillType).getSubSkillTypeDefinition()) >= 1;
    }

    public boolean getPVPEnabled(PrimarySkillType primarySkillType) {
        return pluginRef.getGeneralConfig().getPVPEnabled(primarySkillType);
    }

    public boolean getPVEEnabled(PrimarySkillType primarySkillType) {
        return pluginRef.getGeneralConfig().getPVEEnabled(primarySkillType);
    }

    public boolean getHardcoreStatLossEnabled(PrimarySkillType primarySkillType) {
        return pluginRef.getGeneralConfig().getHardcoreStatLossEnabled(primarySkillType);
    }

    public boolean getHardcoreVampirismEnabled(PrimarySkillType primarySkillType) {
        return pluginRef.getGeneralConfig().getHardcoreVampirismEnabled(primarySkillType);
    }

    public ToolType getPrimarySkillToolType(PrimarySkillType primarySkillType) {
        return primarySkillToolMap.get(primarySkillType);
    }

    public List<SubSkillType> getSubSkills(PrimarySkillType primarySkillType) {
        //TODO: Cache this!
        return new ArrayList<>(primarySkillChildrenMap.get(primarySkillType));
    }

    public double getXpModifier(PrimarySkillType primarySkillType) {
        return ExperienceConfig.getInstance().getFormulaSkillModifier(primarySkillType);
    }

    // TODO: This is a little "hacky", we probably need to add something to distinguish child skills in the enum, or to use another enum for them
    public boolean isChildSkill(PrimarySkillType primarySkillType) {
        switch (primarySkillType) {
            case SALVAGE:
            case SMELTING:
                return true;

            default:
                return false;
        }
    }

    /**
     * Get the localized name for a {@link PrimarySkillType}
     * @param primarySkillType target {@link PrimarySkillType}
     * @return the localized name for a {@link PrimarySkillType}
     */
    public String getLocalizedSkillName(PrimarySkillType primarySkillType) {
        //TODO: Replace with current impl
        return StringUtils.getCapitalized(LocaleLoader.getString(StringUtils.getCapitalized(primarySkillType.toString()) + ".SkillName"));
    }

    public boolean doesPlayerHaveSkillPermission(PrimarySkillType primarySkillType, Player player) {
        return Permissions.skillEnabled(player, primarySkillType);
    }

    public boolean canCombatSkillsTrigger(PrimarySkillType primarySkillType, Entity target) {
        return (target instanceof Player || (target instanceof Tameable && ((Tameable) target).isTamed())) ? getPVPEnabled(primarySkillType) : getPVEEnabled(primarySkillType);
    }

    public String getCapitalizedPrimarySkillName(PrimarySkillType primarySkillType) {
        return StringUtils.getCapitalized(primarySkillType.toString());
    }

    public int getSuperAbilityCooldown(SuperAbilityType superAbilityType) {
        return pluginRef.getGeneralConfig().getCooldown(superAbilityType);
    }

    public int getSuperAbilityMaxLength(SuperAbilityType superAbilityType) {
        return pluginRef.getGeneralConfig().getMaxLength(superAbilityType);
    }

    public String getSuperAbilityOnLocaleKey(SuperAbilityType superAbilityType) {
        return "SuperAbility." + StringUtils.getPrettyCamelCaseName(superAbilityType) + ".On";
    }

    public String getSuperAbilityOffLocaleKey(SuperAbilityType superAbilityType) {
        return "SuperAbility." + StringUtils.getPrettyCamelCaseName(superAbilityType) + ".Off";
    }

    public String getSuperAbilityOtherPlayerActivationLocaleKey(SuperAbilityType superAbilityType) {
        return "SuperAbility." + StringUtils.getPrettyCamelCaseName(superAbilityType) + ".Other.On";
    }

    public String getSuperAbilityOtherPlayerDeactivationLocaleKey(SuperAbilityType superAbilityType) {
        return "SuperAbility." + StringUtils.getPrettyCamelCaseName(superAbilityType) + "Other.Off";
    }

    public String getSuperAbilityRefreshedLocaleKey(SuperAbilityType superAbilityType) {
        return "SuperAbility." + StringUtils.getPrettyCamelCaseName(superAbilityType) + ".Refresh";
    }

    /**
     * Get the permissions for this ability.
     *
     * @param player Player to check permissions for
     * @param superAbilityType target super ability
     * @return true if the player has permissions, false otherwise
     */
    public boolean superAbilityPermissionCheck(SuperAbilityType superAbilityType, Player player) {
        switch (superAbilityType) {
            case BERSERK:
                return Permissions.berserk(player);

            case BLAST_MINING:
                return Permissions.remoteDetonation(player);

            case GIGA_DRILL_BREAKER:
                return Permissions.gigaDrillBreaker(player);

            case GREEN_TERRA:
                return Permissions.greenTerra(player);

            case SERRATED_STRIKES:
                return Permissions.serratedStrikes(player);

            case SKULL_SPLITTER:
                return Permissions.skullSplitter(player);

            case SUPER_BREAKER:
                return Permissions.superBreaker(player);

            case TREE_FELLER:
                return Permissions.treeFeller(player);

            default:
                return false;
        }
    }

    public @NotNull List<PrimarySkillType> getChildSkills() {
        return CHILD_SKILLS;
    }

    public @NotNull ImmutableList<PrimarySkillType> getNonChildSkills() {
        return NON_CHILD_SKILLS;
    }

    public @NotNull ImmutableList<PrimarySkillType> getCombatSkills() {
        return COMBAT_SKILLS;
    }

    public @NotNull ImmutableList<PrimarySkillType> getGatheringSkills() {
        return GATHERING_SKILLS;
    }

    public @NotNull ImmutableList<PrimarySkillType> getMiscSkills() {
        return MISC_SKILLS;
    }

    //    /**
//     * Check if a block is affected by this ability.
//     *
//     * @param blockState the block to check
//     * @param superAbilityType target super ability
//     * @return true if the block is affected by this ability, false otherwise
//     */
//    public boolean superAbilityBlockCheck(SuperAbilityType superAbilityType, BlockState blockState) {
//        switch (superAbilityType) {
//            case BERSERK:
//                return (BlockUtils.affectedByGigaDrillBreaker(blockState) || blockState.getType() == Material.SNOW);
//
//            case GIGA_DRILL_BREAKER:
//                return BlockUtils.affectedByGigaDrillBreaker(blockState);
//
//            case GREEN_TERRA:
//                return BlockUtils.canMakeMossy(blockState);
//
//            case SUPER_BREAKER:
//                return BlockUtils.affectedBySuperBreaker(blockState);
//
//            case TREE_FELLER:
//                dfss
//
//            default:
//                return false;
//        }
//    }
}
