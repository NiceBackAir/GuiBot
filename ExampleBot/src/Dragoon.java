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

	public Dragoon(Unit u, Game game) {
		super(u, game);
		// TODO Auto-generated constructor stub
		cancelFrames = 5;
		scale = 2*32;
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
						
						u.attack(target);
						gotCommand = true;
					} else {
//							game.drawTextMap(u.getPosition(),"charging");
					}
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
						moveAwayFrom(target.getPosition());
//						game.drawTextMap(u.getPosition(), ""+u.getGroundWeaponCooldown());
					}
				}
//				game.drawTextMap(u.getPosition(),""+u.getLastCommand().getUnitCommandType());
			} else {
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
			move(pos);
			gotCommand = true;
		} else {
			game.drawTextMap(u.getPosition(), "busy atk");
		}
	}

}
