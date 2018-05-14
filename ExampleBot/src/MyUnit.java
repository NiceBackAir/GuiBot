import java.util.HashMap;
import java.util.Map;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.PositionedObject;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwapi.WeaponType;
import bwta.BWTA;
import bwta.Chokepoint;

public class MyUnit extends PositionedObject {
	protected Unit u;
//	protected Position objective;
	protected Position attackPoint;
	protected Unit target;
	protected Position retreatPoint;
	protected int scale;
	protected UnitState state;
	protected Squad squad;
	protected boolean gotCommand;
	protected Game game;
	protected int cancelFrames;
	protected Position techPosition;
	protected boolean isRequestingEvac;
	
	public static MyUnit createFrom(Unit u, Game game) {
		if (u == null) {
			throw new RuntimeException("MyUnit: unit is null");
		}
		MyUnit unit = new MyUnit(u, game);
		return unit;
	}
	
	public MyUnit(Unit u, Game game) {
		if (u == null) {
			throw new RuntimeException("MyUnit: unit is null");
		}
		this.game = game;
		this.u = u;
		scale = 5*32;
		techPosition = null;
		//stupid goon cancel frames
		cancelFrames = 0;
		gotCommand = false;
		squad = null;
	}

	@Override
	public Position getPosition() {
		// TODO Auto-generated method stub
		return u.getPosition();
	}
	
	public Unit getClosestPatch() {
		Unit closestPatch = null;
		for(Unit neutralUnit: u.getUnitsInRadius(u.getType().sightRange())) {
			if(neutralUnit.getType().isMineralField() && (closestPatch == null 
				|| u.getPosition().getApproxDistance(neutralUnit.getPosition()) 
				< u.getPosition().getApproxDistance(closestPatch.getPosition()))) {
				
				closestPatch = neutralUnit;
			}
		}
		return closestPatch;
	}
	public void command(UnitState myCommand, Position pos) {		
	}
	public void command(UnitState myCommand, Chokepoint choke, int range) throws Exception {		
		if(myCommand == UnitState.HOLDING) {
			blockChoke(choke);
		}
	}
	
	public void moveDownGradient() {
		
	}
	
	public void scout() {
	}
	
	public void attack(Position pos, boolean attackBuildings) throws Exception {
		target = getTarget(attackBuildings);
//		if(u.isStuck())
//			game.sendText("stuck");	
		if(target != null) {
			if(isFree(target)) {
				if(u.getGroundWeaponCooldown() == 0) {
					if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit
						&& !u.getLastCommand().getTarget().equals(target)) && u.isInWeaponRange(target)) {
					
						u.attack(target);
						gotCommand = true;
					}
//				} else {
//					move(target.getPosition());
//					gotCommand = true;
				}
//				game.drawTextMasp(u.getPosition(),""+u.getLastCommand().getUnitCommandType());
			} else {
//				game.drawTextMap(u.getPosition(), "busy");
			}
		} else if(isFree()) {
//			if((u.getLastCommand().getUnitCommandType() != UnitCommandType.Attack_Move
//				&& !u.getLastCommand().getTargetPosition().equals(pos))
//				|| u.getGroundWeaponCooldown() == 0)
//				
//				u.attack(pos);
//			if(u.getGroundWeaponCooldown() == 0) {
//				if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Move
//					&& !u.getLastCommand().getTargetPosition().equals(pos)))
			
//				u.attack(pos);
				
//			} else
				move(pos);
				gotCommand = true;
//			game.drawTextMap(u.getPosition(),""+u.getLastCommand().getUnitCommandType());
		} else {
//			game.drawTextMap(u.getPosition(), "busy atk");
		}
	}
//	public boolean[] attack(Unit hisUnit) {
//		if(isFree(hisUnit)) {
//			u.attack(hisUnit);
//		}
//		return null;
//	}
	public boolean[] holdPosition() throws Exception {
		target = getTarget(false);
		if(isFree() && !u.isHoldingPosition()) {
			u.holdPosition();
			gotCommand = true;
		}
		return null;
	}
	public void move(Position pos) throws Exception {
		target = null;
		if(!gotCommand) {
//			if(u.getLastCommand().getUnitCommandType() == UnitCommandType.Move && (!u.isMoving() || u.isIdle())) {
//				//fix the weird stuck unit glitch
//				u.stop();
//				game.sendText("why");
//			} else
			
			if(u.isAttackFrame() && u.getLastCommand().getUnitCommandType() != UnitCommandType.Stop)
				u.stop();
//			else if(u.getLastCommand().getUnitCommandType() != UnitCommandType.Move || u.getLastCommand().getTargetPosition() != pos)
			else
				u.move(pos);
//			game.drawLineMap(u.getPosition(), pos, Color.Teal);
		} 
		gotCommand = true;
	}
	public void moveAwayFrom(Position pos) throws Exception {
		double d = u.getPosition().getApproxDistance(pos);
		double[] moveVector = {(u.getX()-pos.getX())*scale/d, (u.getY()-pos.getY())*scale/d};
		moveVector = GuiBot.setMagnitude(terrainCorrection(moveVector), scale);
		moveVector = GuiBot.setMagnitude(adjustForWalls(moveVector, u), scale);
 		
		Position destination = new Position(u.getX()+(int)moveVector[0], u.getY()+(int)moveVector[1]);

		move(destination);

 		gotCommand = true;
 		game.drawLineMap(u.getPosition(), destination, Color.White);
	}
	
	/** Surround enemies like water, literally
	 * 
	 */
	public void surround(Position pos, Position origin, int R) throws Exception {
		double d = pos.getApproxDistance(origin);
		double[] flowVector = {(pos.getX()-origin.getX())/d, (pos.getY()-origin.getY())/d};
		double[] moveVector = new double[2];
		double r = u.getPosition().getApproxDistance(pos);
		double[] rVector = {(pos.getX()-u.getX())/r, (pos.getY()-u.getY())/r};
		double cosTheta = flowVector[0]*rVector[0] + flowVector[1]*rVector[1];
		double sinTheta = flowVector[0]*rVector[1] - flowVector[1]*rVector[0];
		double cosFlowAngle = flowVector[0];
		double sinFlowAngle = flowVector[1];
		
		//V_r
		moveVector[0] += (1 - R*R/r/r)*cosTheta*(cosTheta*cosFlowAngle - sinTheta*sinFlowAngle);
		moveVector[1] += (1 - R*R/r/r)*cosTheta*(sinTheta*cosFlowAngle + cosTheta*sinFlowAngle);
		//V_theta
		moveVector[0] += -1*(1 + R*R/r/r)*sinTheta*-1*(sinTheta*cosFlowAngle + cosTheta*sinFlowAngle);
		moveVector[1] += -1*(1 + R*R/r/r)*sinTheta*(cosTheta*cosFlowAngle - sinTheta*sinFlowAngle);		
				
		moveVector = GuiBot.setMagnitude(moveVector, scale);
		moveVector = terrainCorrection(moveVector);
		moveVector = adjustForWalls(moveVector, u);
// 		game.drawLineMap(u.getPosition(), new Position(u.getX() + (int)moveVector[0], u.getY() + (int)moveVector[1]), Color.Blue);
		
 		Position destination = new Position(u.getX()+(int)moveVector[0], u.getY()+(int)moveVector[1]);
			
 		move(destination);
 		gotCommand = true;
	}
	
	public void commandShuttle(Position attackPosition) throws Exception {
		
	}
	public double[] terrainCorrection(double[] moveVector) {
		double d;
		double[] terrainVector = new double[2];
 		//don't walk into walls
 		int tileX = u.getTilePosition().getX();
 		int tileY = u.getTilePosition().getY();
 		int xPos;
 		int yPos;
		for(int x=tileX-1; x<= tileX+1; x++) {
			for(int y=tileY-1;y<=tileY+1;y++) {
				if(x>=0 && x<game.mapWidth() && y>=0 && y<=game.mapHeight() && x != tileX && y != tileY) {
 					xPos = x*32 + 16;
 					yPos = y*32 + 16;
 					//tile is in direction of movement
 					if(((xPos-u.getX())*moveVector[0] + (yPos-u.getY())*moveVector[1])>= 0) {
 						//check for stuff in the way
 						if(GuiBot.walkMap[x][y] > 0 || game.getUnitsOnTile(x, y).size() > 0) {
	     					d = u.getPosition().getApproxDistance(new Position(xPos, yPos));
	     					terrainVector[0] -= (xPos-u.getX())/d;
	     					terrainVector[1] -= (yPos-u.getY())/d;
//	     					terrainVector[0] += -(GuiBot.walkMap[x][y]-GuiBot.walkMap[tileX][tileY])*(xPos-u.getX())/d;
//	     					terrainVector[1] += -(GuiBot.walkMap[x][y]-GuiBot.walkMap[tileX][tileY])*(yPos-u.getY())/d;
				 		} 
 					}
				}
			}
		}

 		terrainVector = GuiBot.setMagnitude(terrainVector, 1);
 		terrainVector = GuiBot.setMagnitude(terrainVector, -terrainVector[0]*moveVector[0]-terrainVector[1]*moveVector[1]);
 		game.drawLineMap(u.getPosition(), new Position(u.getX() + (int)terrainVector[0], u.getY() + (int)terrainVector[1]), Color.Orange);
 		moveVector[0] += terrainVector[0];
 		moveVector[1] += terrainVector[1];
 		return moveVector;
	}
	
    /** Makes hovering units move at full speed even if they reach the end of the map.
     * 
     * @param v			vector to modify
     * @param myUnit	unit who's moving
     * @return			vector pointing somewhere in bounds of the map
     */
    public double[] adjustForWalls(double[] v, Unit myUnit) {
    	double r = Math.sqrt(v[0]*v[0] + v[1]*v[1]);
    	double margin = Math.max(myUnit.getType().width(), myUnit.getType().height())/2.0;
    	double dx = v[0];
    	double dy = v[1];
    	double dir;
    	double xOutside = 0;
    	double yOutside = 0;
    	
    	//fix literal corner cases
    	if(myUnit.getX() + dx < margin) {
    		xOutside = Math.abs(myUnit.getX() + dx - margin);
    	} else if(myUnit.getX() + dx > game.mapWidth()*32 - margin) {
    		xOutside = Math.abs(myUnit.getX() - (game.mapWidth()*32 - margin));
    	} else if(myUnit.getY() + dy < margin) {
    		yOutside = Math.abs(myUnit.getY() + dy - margin);
    	} else if(myUnit.getY() + dy > game.mapHeight()*32 - margin) {
    		yOutside = Math.abs(myUnit.getY() + dy - (game.mapHeight()*32 - margin));
    	} else {
    	}
    	if(xOutside > 0 && yOutside > 0) {
	    	if(xOutside > yOutside) {
	    		dy *= -1;
	    	} else {
	    		dx *= -1;
	    	}
    	}
    	
    	//redirect move vector to stay in map
    	if(myUnit.getX() + dx < margin) {
    		dir = Math.signum(dy);
    		if(dir == 0)
    			dir = Math.signum(myUnit.getX()-game.mapWidth()*32);
    		dx = margin-myUnit.getX();
    		dy = dir*Math.sqrt(r*r-dx*dx);
    	} else if(myUnit.getX() + dx > game.mapWidth()*32 - margin) {
    		dir = Math.signum(dy);
    		if(dir == 0)
    			dir = Math.signum(myUnit.getX()-game.mapWidth()*32);
    		dx = game.mapWidth()*32 - margin - myUnit.getX();
    		dy = dir*Math.sqrt(r*r-dx*dx);
    	} else if(myUnit.getY() + dy < margin) {
    		dir = Math.signum(dx);
    		if(dir == 0)
    			dir = Math.signum(myUnit.getY()-game.mapHeight()*32);
    		dy = margin - myUnit.getY();
    		dx = dir*Math.sqrt(r*r-dy*dy);
    	} else if(myUnit.getY() + dy > game.mapHeight()*32 - margin) {
    		dir = Math.signum(dx);
    		if(dir == 0)
    			dir = Math.signum(myUnit.getY()-game.mapHeight()*32);
    		dy = game.mapHeight()*32 - margin - myUnit.getY();
    		dx = dir*Math.sqrt(r*r-dy*dy);
    	} else {
    	}
    	return new double[] {dx, dy};
    }
	
	public void blockChoke(Chokepoint choke) throws Exception {
		target = getTarget(false);
//		if(u.isStuck()) 
//			game.sendText("stuck");	
		if(choke != null && isFree()) {
			if(u.getType().groundWeapon() != WeaponType.None
				&& GuiBot.intersects(u.getType(), u.getPosition(), choke.getSides().first, choke.getSides().second)) {
				if(game.self().weaponMaxRange(u.getType().groundWeapon()) <= 3*32) {
					//melee units can wall
					if(!u.isHoldingPosition())
						u.holdPosition();
				} else {
					if(target == null) {
						if(u.getLastCommand().getUnitCommandType() != UnitCommandType.Stop)
							u.stop();
					} else {
							attack(target.getPosition(), false);
					}
				}
			} else if(choke.getCenter().getApproxDistance(u.getPosition()) < choke.getWidth()/1.5) {
				if(!u.isHoldingPosition()) {
					if(BWTA.getRegion(u.getPosition()).equals(choke.getRegions().first)) {	
						attack(choke.getRegions().second.getCenter(), false);
					} else {
						attack(choke.getRegions().first.getCenter(), false);
					}
				}
			} else if(target != null) {
				attack(target.getPosition(), false);
			} else {
				attack(choke.getCenter(), false);
			}
		} else {
		}
		gotCommand = true;
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
					&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva ) {
					
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
	
	public Position getTargetPosition() throws Exception {
		HashMap<TilePosition, Double> stormMap = new HashMap<TilePosition, Double>();
		TilePosition tempTile = null;
		int playerFactor;
		double tileFactor;
		double cloakFactor;
		for (Unit unit: u.getUnitsInRadius(13*32)) {
			if(!unit.getType().isBuilding() && !unit.isUnderStorm() && unit.getType() != UnitType.Zerg_Larva
				&& unit.getType() != UnitType.Zerg_Egg && !unit.isLoaded()) {
				
				playerFactor = 0;
				tileFactor = 1;
				cloakFactor = 1;
				
				if(unit.getPlayer() == game.enemy()) {
					playerFactor = 1;
					if(!unit.isDetected() && unit.isVisible()) {
						cloakFactor = 2;
					}
				} else if(unit.getPlayer() == game.self()) {
					playerFactor = -1;
				}
				
				for(int x = -1; x<=1; x++) {
					for(int y = -1; y<=1; y++) {
						if(x==0 && y== 0) {
							tileFactor = 1;
						} else {
							tileFactor = 0.8;
						}
						tempTile = new TilePosition(unit.getTilePosition().getX() +x, unit.getTilePosition().getY() + y);
						if(stormMap.get(tempTile) == null) {
							stormMap.put(tempTile, playerFactor*tileFactor*cloakFactor);
						} else {
							stormMap.replace(tempTile, stormMap.get(tempTile) + playerFactor*tileFactor*cloakFactor);
						}
					}
				}

			}
		}
		
		//find the best storm spot
		TilePosition stormLocation = null;
		double mostCasualties = 0;
		for(Map.Entry<TilePosition, Double> entry: stormMap.entrySet()) {
			//set storm minimum here
			if(entry.getValue() >= 3.5 && entry.getValue() > mostCasualties) {
				mostCasualties = entry.getValue();
				stormLocation = entry.getKey();
			}
		}
		if(stormLocation != null)
			return new Position(stormLocation.getX()*32 + 16, stormLocation.getY()*32 + 16);
		else
			return null;
	}
	
	//default check to see if unit's attack will not be interrupted with another command. Good for zealots, DTs, and Archons
	public boolean isFree() throws Exception {
		if(gotCommand)
			return false;
		if(u.isLoaded())
			return false;	
		if(!u.isAttackFrame() && u.getGroundWeaponCooldown() > 0)
			return true;
		
		if(u.getLastCommand().getUnitCommandType() == UnitCommandType.Use_Tech_Unit)
			return false;
			
		if(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Move				
			|| u.getLastCommand().getUnitCommandType() == UnitCommandType.Hold_Position
			|| u.getLastCommand().getUnitCommandType() == UnitCommandType.Patrol) {
			
			if(target != null && target.exists()
				&& (u.isInWeaponRange(target) || u.getDistance(target) < u.getType().seekRange())) {
				return false;
			}
				
		}
		return true;
	}
	
	//default check to see if unit's attack unit command will not be interrupted with another command. Good for zealots, DTs, and Archons
	public boolean isFree(Unit hisUnit) {
		if(gotCommand)
			return false;
		if(u.isLoaded())
			return false;
		if(!u.isAttackFrame() && u.getGroundWeaponCooldown() > 0)
			return true;
		if(u.getLastCommand().getTarget() != null && u.getLastCommand().getTarget().equals(hisUnit)
			&& hisUnit.isDetected() && hisUnit.getType() != UnitType.Resource_Vespene_Geyser 
			&& hisUnit.exists() && (u.isInWeaponRange(hisUnit) || u.getDistance(hisUnit) <= u.getType().seekRange())) {
			
			return false;
		}
		return true;
	}
	
	public boolean isRequestingEvac() {
		return isRequestingEvac;
	}
	
	//respond to a bulk army retreat command
	public double[] retreat() {
		return null;
	}
	
	//respond to a bulk defend position command
	public double[] defend(Position rallyPoint) {
		return null;
	}
	
	public double[] adjustForCollision() {
		return null;
	}
	
	public double[] dyingWishes() {
		return null;
	}
	
	public void driveShuttle(MyUnit shuttle) {
		
	}
	
	public Unit getUnit() {
		return u;
	}
	public Squad getSquad() {
		return squad;
	}
	public boolean hasCommand() {
		return gotCommand;
	}
	public void setSquad(Squad squad) {
		this.squad = squad;
	}
	public void setCommandGiven(boolean commanded) {
		gotCommand = commanded;
	}
}
