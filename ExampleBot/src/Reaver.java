import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwta.BWTA;

public class Reaver extends MyUnit {

	public Reaver(Unit u, Game game) {
		super(u, game);
		// TODO Auto-generated constructor stub
	}

	public void attack(Position pos, boolean attackBuildings) throws Exception {
		if(u.isLoaded())
			isRequestingEvac = false;
		target = getTarget(attackBuildings);
		if(target != null) {
			if(isFree(target)) {
				if((u.getGroundWeaponCooldown() == 0 || u.isAttackFrame()) && u.getScarabCount() > 0) {
					if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit
						&& !u.getLastCommand().getTarget().equals(target))) {
					
//						isRequestingEvac = true;
						u.attack(target);						
						gotCommand = true;
					}
				} else {
					if(u.getDistance(target) > 8*32) {
						move(target.getPosition());
					} else if(target.getType().canMove() || target.getType() == UnitType.Terran_Bunker) {
						moveAwayFrom(target.getPosition());
					}
					
					if(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit 
						|| u.getLastCommand().getUnitCommandType() == UnitCommandType.Train) {
						isRequestingEvac = true;
					}
				}
			} else {
			}
		} else if(isFree()) {
			isRequestingEvac = true;
			move(pos);
//			gotCommand = true;
		} else {
			isRequestingEvac = true;
		}
		game.drawTextMap(u.getPosition(),""+isRequestingEvac() + " " + u.getLastCommand().getUnitCommandType());
	}
	
	public Unit getTarget(boolean attackBuildings) throws Exception {
		Unit closestEnemy = null;
		int range = Math.max(u.getType().seekRange(), game.self().weaponMaxRange(u.getType().groundWeapon()));
		if(range == 0 || !u.getType().canAttack()) {
			range = u.getType().sightRange();
		}
//		System.out.println(range);
		for(Unit hisUnit: u.getUnitsInRadius(range)) {// u.getUnitsInWeaponRange(u.getType().groundWeapon())) {
			if(hisUnit.getPlayer() == game.enemy()) {
				if(hisUnit.isDetected() && !hisUnit.isInvincible() //u.isInWeaponRange(hisUnit) && 
					&& (hisUnit.isCompleted() || hisUnit.getType().isBuilding() || hisUnit.getType() == UnitType.Zerg_Lurker_Egg)
					&& (attackBuildings || !hisUnit.getType().isBuilding() || hisUnit.getType().canAttack()
					|| hisUnit.getType() == UnitType.Terran_Bunker)					
					&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva
					&& BWTA.getGroundDistance(u.getTilePosition(), hisUnit.getTilePosition()) < range) {
					
					if(closestEnemy == null || hisUnit.getDistance(u) < closestEnemy.getDistance(u)) {
						closestEnemy = hisUnit;
					}
				}
			}
		}
//		if(closestEnemy != null)
//			game.drawLineMap(u.getPosition(), closestEnemy.getPosition(), Color.White);
		return closestEnemy;
	}
}
