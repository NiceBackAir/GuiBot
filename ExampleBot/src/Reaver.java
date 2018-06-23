import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwta.BWTA;

public class Reaver extends MyUnit {
	private boolean shotsFired;
	
	public Reaver(Unit u, Game game) {
		super(u, game);
		// TODO Auto-generated constructor stub
		shotsFired = false;
	}

	public void attack(Position pos, boolean attackBuildings) throws Exception {
		target = getTarget(attackBuildings);
		
		if(u.isLoaded()) {
			isRequestingEvac = false;
			shotsFired = false;
		} else if(target != null && target.exists()) {
//			game.drawLineMap(u.getPosition(), target.getPosition(), Color.Red);
			if(isFree(target)) {
				if((u.getGroundWeaponCooldown() == 0 || u.isAttackFrame()) && u.getScarabCount() > 0) {
					if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit
						&& !u.getLastCommand().getTarget().equals(target))) {
					
						u.attack(target);						
						gotCommand = true;
						shotsFired = true;
					} else {
					}
				} else {
					if(u.getDistance(target) > 8*32) {
						move(target.getPosition());
					} else if(target.getType().canMove() || target.getType() == UnitType.Terran_Bunker) {
						moveAwayFrom(target.getPosition());
					}
					
					if(shotsFired) {
						isRequestingEvac = true;
					}
				}
			} else {
			}
		} else if(isFree()) {
			if(u.getGroundWeaponCooldown() == 0 || shotsFired) {
				isRequestingEvac = true;
			}
			move(pos);
		} else {
			if(shotsFired || !u.isAttackFrame()) {
				isRequestingEvac = true;
			}
		}
//		game.drawTextMap(u.getPosition(),"  "+isRequestingEvac() + " " + u.getLastCommand().getUnitCommandType());
	}
	
	public Unit getTarget(boolean attackBuildings) throws Exception {
		Unit closestEnemy = null;
		int range = 8*32 + 16;
//		System.out.println(range);
		double groundDistance = 0;
		double closestGroundDistance = 9999;
		for(Unit hisUnit: u.getUnitsInRadius(range)) {// u.getUnitsInWeaponRange(u.getType().groundWeapon())) {
			if(hisUnit.getPlayer() == game.enemy()) {
				if(!hisUnit.isInvincible() && !hisUnit.isFlying() //u.isInWeaponRange(hisUnit) && hisUnit.isDetected() && 
					&& (hisUnit.isCompleted() || hisUnit.getType().isBuilding() || hisUnit.getType() == UnitType.Zerg_Lurker_Egg)
					&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)					
					&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva) {
//					&& hisUnit.hasPath(u)
					
					if(BWTA.getRegion(u.getPosition()) == BWTA.getRegion(hisUnit.getPosition()))
						groundDistance = hisUnit.getDistance(u);
					else
						groundDistance = BWTA.getGroundDistance(hisUnit.getTilePosition(), u.getTilePosition()) - 32;
					
					if (groundDistance < range && groundDistance >= 0) {
						if(closestEnemy == null || groundDistance < closestGroundDistance) {
							closestEnemy = hisUnit;
							closestGroundDistance = groundDistance;
						}
					}
				}

			}
		}
		if(closestEnemy != null)
			game.drawLineMap(u.getPosition(), closestEnemy.getPosition(), Color.White);
		return closestEnemy;
	}
}
