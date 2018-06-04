import bwapi.*;

public class HisUnit extends PositionedObject {
	private Unit u;
	private UnitType type;
	private int remainingBuildTime;
	private Position lastPosition;
	private int timeSinceVisible;
	private boolean isCompleted;
	private int groundWeaponCooldown;
	private int airWeaponCooldown;
	private UpgradeType currentUpgrade;
	private TechType currentTech;
	private Game game;
	
	//constructor blob
	public HisUnit(Game game) {
		super();
		u = null;
		type = UnitType.Unknown;
		currentUpgrade = null;
		currentTech = null;
		remainingBuildTime = 0;
		lastPosition = null;
		timeSinceVisible = 0;
		isCompleted = false;
		groundWeaponCooldown = 0;
		airWeaponCooldown = 0;
		this.game = game;
	}	
	public HisUnit(Unit hisUnit, Game game) {
		u = hisUnit;
		remainingBuildTime = 0;
		lastPosition = hisUnit.getPosition();
		timeSinceVisible = 0;
		if(hisUnit.isCompleted()) {
			isCompleted = true;
			remainingBuildTime = 0;
		} else {
			isCompleted = false;
			if(hisUnit.getType().isBuilding())
				remainingBuildTime = (hisUnit.getType().buildTime()*(hisUnit.getType().maxHitPoints()-hisUnit.getHitPoints()))/hisUnit.getType().maxHitPoints();
		}
		this.game = game;
	}
	
	/**keeps track of timers when the unit is not visible, updates typing if it is visible.
	 * 
	 */
	public void update() {
		if(u.isVisible()) {
			timeSinceVisible = 0;
			type = u.getType();
			lastPosition = u.getPosition();
			groundWeaponCooldown = u.getGroundWeaponCooldown();
			airWeaponCooldown = u.getAirWeaponCooldown();
			if(u.isMorphing()) {
				remainingBuildTime = (u.getType().buildTime()*(u.getType().maxHitPoints()-u.getHitPoints()))/u.getType().maxHitPoints();
			}
			if(u.isUpgrading()) {
				currentUpgrade = u.getUpgrade();
			}
			if(u.isResearching()) {
				currentTech = u.getTech();
			}
		} else {
			timeSinceVisible++;
			groundWeaponCooldown = Math.max(0, groundWeaponCooldown-1);
			airWeaponCooldown = Math.max(0, airWeaponCooldown-1);
			if(lastPosition != null && game.isVisible(lastPosition.toTilePosition())) {
				lastPosition = null;
			}
		}
		
		
		if(remainingBuildTime == 0)
			isCompleted = true;
		remainingBuildTime = Math.max(0, remainingBuildTime-1);
	}
	
	@Override
	public Position getPosition() {
		// TODO Auto-generated method stub
		return lastPosition;
	}
	
	//getter blob
	public Unit getUnit() {
		return u;
	}	
	public UnitType getType() {
		return type;
	}	
	public int getRemainingBuildTime() {
		return remainingBuildTime;
	}
	public int getGroundWeaponCooldown() {
		return groundWeaponCooldown;
	}
	public int getAirWeaponCooldown() {
		return airWeaponCooldown;
	}
	public UpgradeType getUpgrade() {
		return currentUpgrade;
	}
	public int getTimeSinceVisible() {
		return timeSinceVisible;
	}
	public TechType getTech() {
		return currentTech;
	}
	public boolean isCompleted() {
		return isCompleted;
	}
	public int getRange() {
		Player player = game.enemy();
		int range = 0;
		if(u.getType().groundWeapon() != WeaponType.None)
			range = player.weaponMaxRange(u.getType().groundWeapon());
		if(u.getType().airWeapon() != WeaponType.None)
			range = Math.max(range, player.weaponMaxRange(u.getType().airWeapon()));		
		if(u.getType() == UnitType.Terran_Bunker)
			range = player.weaponMaxRange(WeaponType.Gauss_Rifle);
		return range;
	}
}
