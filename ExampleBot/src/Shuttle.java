import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import bwta.BWTA;

public class Shuttle extends MyUnit {
	boolean hasReaver;
	public Shuttle(Unit u, Game game) {
		super(u, game);
		// TODO Auto-generated constructor stub
		hasReaver = false;
	}

	public void commandShuttle(HisBase dropBase) throws Exception {
		hasReaver = false;
		//target = getTarget(false);
    	double[] moveVector = {0,0};
    	for(Unit myUnit: u.getUnitsInRadius(32)) {
    		if(myUnit.getPlayer() == game.self() && myUnit.isCompleted()) {
    			if(myUnit.getType() == UnitType.Protoss_Reaver
    				&& u.getSpaceRemaining() >= UnitType.Protoss_Reaver.spaceRequired()
    				&& (myUnit.getScarabCount() + myUnit.getTrainingQueue().size() == 5 || game.self().minerals() < 15)
    				&& (GuiBot.myUnits.containsKey(myUnit.getID()) && GuiBot.myUnits.get(myUnit.getID()).isRequestingEvac())
//    				&& (target == null || myUnit.isUnderAttack())
    				) {
    				
//    				u.load(myUnit);
    				myUnit.rightClick(u);
    				u.rightClick(myUnit);
    				gotCommand = true;
    			} else if(myUnit.getType() == UnitType.Protoss_Dragoon
    				&& u.getSpaceRemaining() >= UnitType.Protoss_Dragoon.spaceRequired() && !myUnit.isAttackFrame() 
    				&& (myUnit.isUnderAttack() && myUnit.getShields() + myUnit.getHitPoints() <= 50)
    				&& (GuiBot.myUnits.containsKey(myUnit.getID()) && GuiBot.myUnits.get(myUnit.getID()).isRequestingEvac())
    				) {
    				
    				u.rightClick(myUnit);
    				myUnit.rightClick(u);
    				gotCommand = true;
    			}
    		}
    	}
		for(Unit myUnit: u.getLoadedUnits()) {
			if(myUnit.getType() == UnitType.Protoss_Reaver) {
				hasReaver = true;
			}
		}
		
    	double[] threatVector = threatVector();
    	double[] dropVector = {0,0};
    	double[] clusterVector = {0,0};
		if(hasReaver && dropBase != null) {
			dropVector = drop(dropBase);
		} else {
    		clusterVector = clusterVector();
    	}
    	double[] terrainVector = terrainVector();
    	double[] targetVector = targetVector();
    	moveVector = addVector(moveVector, threatVector);
    	moveVector = addVector(moveVector, dropVector);
    	moveVector = addVector(moveVector, clusterVector);
     	moveVector = addVector(moveVector, terrainVector);
     	moveVector = addVector(moveVector, targetVector);
     	
		moveVector = GuiBot.setMagnitude(moveVector, scale);
		moveVector = adjustForWalls(moveVector, u);
		
		//drop units
		if(u.canUnload() && moveVector[0]*threatVector[0] + moveVector[1]*threatVector[1] <= 0) {
			if(!gotCommand) {
				for(Unit myUnit: u.getLoadedUnits()) {
					if(GuiBot.myUnits.containsKey(myUnit.getID())) {
						MyUnit unit = GuiBot.myUnits.get(myUnit.getID());
						if(myUnit.getType() == UnitType.Protoss_Reaver) {
							hasReaver = true;
							if(unit.getTarget(false) != null) 
								u.unload(myUnit);
						} else if(unit.getTarget(false) == null) {
							u.unload(myUnit);
						}
					}
				}
			}
 		}
//		game.drawLineMap(u.getX(), u.getY(), u.getX()+(int)threatVector[0], u.getY()+(int)threatVector[1], Color.Red);
//		game.drawLineMap(u.getX(), u.getY(), u.getX()+(int)moveVector[0], u.getY()+(int)moveVector[1], Color.Green);

		move(new Position(u.getX()+(int)moveVector[0], u.getY()+(int)moveVector[1]));
	}
	
	public double[] addVector(double[] a, double[] b) {
		if(b != null)
			return new double[] {a[0] + b[0], a[1] + b[1]};
		else
			return a;
	}
	public double[] terrainVector() {
     	double[] terrainVector = {0,0};
     	double rectDist;
 		for(int x=0; x<=(game.mapWidth())*32-1; x+=(game.mapWidth())*32-1) {
 			for(int y=0; y<=(game.mapHeight())*32-1; y+=(game.mapHeight())*32-1) {
				rectDist = Math.abs(x-u.getX()) + Math.abs(y-u.getY());
				terrainVector[0] += -3*scale*scale/rectDist/rectDist*(x-u.getX());
				terrainVector[1] += -3*scale*scale/rectDist/rectDist*(y-u.getY());
 			}
 		}
 		return terrainVector;
	}
	
	public double[] threatVector() {
		double d;
     	double mySize = Math.sqrt(Math.pow(u.getType().width(),2) + Math.pow(u.getType().height(),2))/2;
     	double hisSize;
     	double hisRange;
     	double[] threatVector = {0,0};
		for(Unit hisUnit: u.getUnitsInRadius(u.getType().sightRange() + 2*32)) {
			if(hisUnit.getPlayer().equals(game.enemy())				
				&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)) {
				d = u.getPosition().getApproxDistance(hisUnit.getPosition());
				hisSize = Math.sqrt(Math.pow(hisUnit.getType().width(),2) + Math.pow(hisUnit.getType().height(),2))/2;	 
				
				if (!hisUnit.getType().airWeapon().equals(WeaponType.None)) {
					hisRange = game.enemy().weaponMaxRange(hisUnit.getType().airWeapon()) + 16 + hisSize + mySize;
    				threatVector[0] += -3*scale*hisRange/d/d*(hisUnit.getX()-u.getX());
					threatVector[1] += -3*scale*hisRange/d/d*(hisUnit.getY()-u.getY());
				} else if(u.getLoadedUnits().size() > 0 
					&& (!hisUnit.getType().groundWeapon().equals(WeaponType.None) && !hisUnit.getType().isWorker())
					|| hisUnit.getType() == UnitType.Terran_Bunker) {		    
					if(hisUnit.getType() == UnitType.Terran_Bunker)
						hisRange = game.enemy().weaponMaxRange(WeaponType.Gauss_Rifle) + 16 + hisSize + mySize;
					else
						hisRange = game.enemy().weaponMaxRange(hisUnit.getType().groundWeapon()) + hisSize + mySize;
    				threatVector[0] += -2.5*scale*hisRange/Math.max(4*32,d)/d*(hisUnit.getX()-u.getX());
					threatVector[1] += -2.5*scale*hisRange/Math.max(4*32,d)/d*(hisUnit.getY()-u.getY());
				}
			}
		}	
		
		return threatVector;
	}
	
	public double[] drop(HisBase dropBase) {
		double d;
		double[] dropVector = {0,0};
		d = dropBase.getPosition().getApproxDistance(u.getPosition());
		dropVector[0] = 1*scale/d*(dropBase.getPosition().getX()-u.getX());
		dropVector[1] = 1*scale/d*(dropBase.getPosition().getY()-u.getY());

		return dropVector;
	}
	
	public double[] clusterVector() {
		double d;
     	double[] clusterVector = {0,0};
     	boolean pickUpReaver = false;
     	if(!hasReaver && game.self().allUnitCount(UnitType.Protoss_Reaver) > 0) {
			for(Unit otherUnit: game.self().getUnits()) {
				if(otherUnit.getType() == UnitType.Protoss_Reaver && !otherUnit.isLoaded()
					&& (otherUnit.isCompleted() || otherUnit.getRemainingBuildTime()*game.self().topSpeed(u.getType()) 
					< otherUnit.getPosition().getApproxDistance(u.getPosition()))) {    		
					
					d = u.getPosition().getApproxDistance(otherUnit.getPosition());
					clusterVector[0] += 4*scale/d*(otherUnit.getX()-u.getX());
					clusterVector[1] += 4*scale/d*(otherUnit.getY()-u.getY());
					clusterVector[0] += 3*scale*scale/d/d*(otherUnit.getX()-u.getX());
					clusterVector[1] += 3*scale*scale/d/d*(otherUnit.getY()-u.getY());
					pickUpReaver = true;
				}
			}
     	} 
     	if(!pickUpReaver) {
			for(Unit otherUnit: game.self().getUnits()) {
				if(otherUnit.isCompleted() && !otherUnit.isLoaded()) {
					if(otherUnit.getType() == UnitType.Protoss_Dragoon) {
						d = u.getPosition().getApproxDistance(otherUnit.getPosition());
						double damageFactor = 1;
						if(otherUnit.getShields() < otherUnit.getType().maxShields())
							damageFactor = 3;
						if((otherUnit.isUnderAttack() && otherUnit.getShields() + otherUnit.getHitPoints() <= 50)) {
							clusterVector[0] += 0.4*damageFactor*scale/d*(otherUnit.getX()-u.getX());
							clusterVector[1] += 0.4*damageFactor*scale/d*(otherUnit.getY()-u.getY());
							clusterVector[0] += 1*damageFactor*scale*8*32/d/d*(otherUnit.getX()-u.getX());
							clusterVector[1] += 1*damageFactor*scale*8*32/d/d*(otherUnit.getY()-u.getY());
						} if(d > 32) {
							clusterVector[0] += 0.2*damageFactor*scale/d*(otherUnit.getX()-u.getX());
							clusterVector[1] += 0.2*damageFactor*scale/d*(otherUnit.getY()-u.getY());
							clusterVector[0] += 0.2*damageFactor*scale*8*32/d/d*(otherUnit.getX()-u.getX());
							clusterVector[1] += 0.2*damageFactor*scale*8*32/d/d*(otherUnit.getY()-u.getY());
						} else {
							clusterVector[0] -= 0.2/damageFactor*scale/d*(otherUnit.getX()-u.getX());
							clusterVector[1] -= 0.2/damageFactor*scale/d*(otherUnit.getY()-u.getY());
						}
					} else if(otherUnit.getType() == UnitType.Protoss_Corsair && hasReaver) {
						d = u.getPosition().getApproxDistance(otherUnit.getPosition());
						clusterVector[0] += 0.2*scale/d*(otherUnit.getX()-u.getX());
						clusterVector[1] += 0.2*scale/d*(otherUnit.getY()-u.getY());
						clusterVector[0] += 0.2*scale*8*32/d/d*(otherUnit.getX()-u.getX());
						clusterVector[1] += 0.2*scale*8*32/d/d*(otherUnit.getY()-u.getY());
					}
				} 
			}
     	}
		//go back home if nothing to do
		if(clusterVector[0] == 0 && clusterVector[1] == 0) {
			d = u.getPosition().getApproxDistance(game.self().getStartLocation().toPosition());
			clusterVector[0] = 2*scale/d*(game.self().getStartLocation().toPosition().getX()-u.getX());
			clusterVector[1] = 2*scale/d*(game.self().getStartLocation().toPosition().getY()-u.getY());
		}
		return clusterVector;
	}
	
	public double[] targetVector() {
		double[] targetVector = {0,0};
		double targetScore = 0;
		double bestTargetScore = 0;
		if(hasReaver) {
			double d;
			for(Unit hisUnit: u.getUnitsInRadius(u.getType().sightRange())) {
				if(hisUnit.getPlayer().equals(game.enemy())	
					&& !hisUnit.isInvincible() && hisUnit.getType() != UnitType.Zerg_Larva && !hisUnit.isFlying()
					&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Resource_Vespene_Geyser
					&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)) {
					
					d = u.getPosition().getApproxDistance(hisUnit.getPosition());
					targetScore = 1.8*scale*8*32/Math.max(6*32, d);
					if(targetScore > bestTargetScore) {
						targetVector[0] = targetScore/d*(hisUnit.getX()-u.getX());
						targetVector[1] = targetScore/d*(hisUnit.getY()-u.getY());
						bestTargetScore = targetScore;
					}
				}
			}	
		}
		return targetVector;
	}
	
//	public Unit getTarget(boolean attackBuildings) throws Exception {
//		Unit closestEnemy = null;
//		int range = u.getType().sightRange() + 32;
//		for(Unit hisUnit: u.getUnitsInRadius(range)) {// u.getUnitsInWeaponRange(u.getType().groundWeapon())) {
//			if(hisUnit.getPlayer() == game.enemy()) {
//				if(hisUnit.isDetected() && !hisUnit.isInvincible() && !hisUnit.isFlying()// && u.canAttack(hisUnit, true, false, false)
//					&& (attackBuildings || !hisUnit.getType().isBuilding() || hisUnit.getType().canAttack()
//					|| hisUnit.getType() == UnitType.Terran_Bunker)					
//					&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva ) {
//					
//					if(closestEnemy == null || hisUnit.getDistance(u) < closestEnemy.getDistance(u)) {
//						closestEnemy = hisUnit;
//					}
//				}
//			}
//		}
//		return closestEnemy;
//	}
	
	public double[] dyingWishes() {
		if(u.canUnloadAtPosition(u.getPosition())) {
			u.unloadAll(u.getPosition());
			gotCommand = true;
		}
		return null;
	}	
}
