import java.io.BufferedWriter;
import java.io.FileWriter;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwapi.WeaponType;

public class Dragoon extends MyUnit {
	private boolean shotsFired;

	public Dragoon(Unit u, Game game) {
		super(u, game);
		// TODO Auto-generated constructor stub
		cancelFrames = 5;
		scale = 2*32;
		shotsFired = false;
	}
	
	public void attack(Position pos, boolean attackBuildings) throws Exception {
		target = getTarget(attackBuildings);
		if(u.isStuck())
			game.drawTextMap(u.getPosition(),"stuck");
		if(target != null) {
			if(isFree(target)) {
				if(u.getGroundWeaponCooldown() == 0 || u.isAttackFrame()) {
					if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit
						&& !u.getLastCommand().getTarget().equals(target))) {
						
						u.attack(target, false);
						gotCommand = true;
						shotsFired = true;
					} else {
//						game.drawTextMap(u.getPosition(),"charging");
					}
				} else {
					if(attackBuildings) {
						if(pos.getApproxDistance(u.getPosition()) > 48)
							move(pos);
						else
							moveAwayFrom(pos);
					} else {
						int myRange = game.self().weaponMaxRange(u.getType().groundWeapon());
						double hisSpeed = game.enemy().topSpeed(target.getType());
						if(target.isStimmed())
							hisSpeed *= 1.5;
						int hisRange = game.enemy().weaponMaxRange(target.getType().groundWeapon());
						if(!u.isInWeaponRange(target)) {
							move(target.getPosition());
						} else if(target.getType().canMove() || target.getType() == UnitType.Terran_Bunker) {
	//						&&	Math.max(myRange - hisRange, u.getDistance(target)) + 8*(u.getType().topSpeed() - hisSpeed) > 0 ) {
							//kiting behavior
							moveDownGradient(threatVector());
//							moveAwayFrom(pos);
//							game.drawTextMap(u.getPosition(), ""+u.getGroundWeaponCooldown());
						}
					}
					if(shotsFired) {
						isRequestingEvac = true;
					}
				}
//				game.drawTextMap(u.getPosition(),"1");
//				game.drawTextMap(u.getPosition(),""+u.getLastCommand().getUnitCommandType());
			} else {
//				game.drawTextMap(u.getPosition(),"2");
			}
		} else if(isFree()) {
//			if((u.getLastCommand().getUnitCommandType() != UnitCommandType.Attack_Move
//				&& !u.getLastCommand().getTargetPosition().equals(pos))
//				|| u.getGroundWeaponCooldown() == 0)
//				
//				u.attack(pos);
//			if(u.getGroundWeaponCooldown() == 0) {
//			if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Move
//				&& !u.getLastCommand().getTargetPosition().equals(pos))) {
//		
//				u.attack(pos);
//			}
//			} else
			if(u.getGroundWeaponCooldown() == 0 || shotsFired) {
				isRequestingEvac = true;
			}
			move(pos);
			gotCommand = true;
//			game.drawTextMap(u.getPosition(),"3");
		} else {
			if(shotsFired) {
				isRequestingEvac = true;
			}
//			game.drawTextMap(u.getPosition(), "busy atk");
//			game.drawTextMap(u.getPosition(),"4");
		}
		
		if(u.isLoaded()) {
			isRequestingEvac = false;
			shotsFired = false;
		}
	}

}
