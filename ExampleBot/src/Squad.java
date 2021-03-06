import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwapi.WeaponType;
import bwta.BaseLocation;
import bwta.Chokepoint;

public class Squad {
	private ArrayList<MyUnit> units;
	private UnitState command;
	private Position objective;
	private double powerAtObjective;
	private int radius;
	private BaseLocation base;
	private Game game;
	private boolean seesRangedEnemies;
	private Position center;
	private HashMap<UnitType, Integer> unitTab;
	
	public Squad(Game game) {
		units = new ArrayList<MyUnit>();
		this.game = game;
		unitTab = new HashMap<UnitType, Integer>();
	}
	public Squad(ArrayList<MyUnit> myUnits, Game game) {
		units = myUnits;
		this.game = game;
		unitTab = new HashMap<UnitType, Integer>();
	}
	
	public void add(MyUnit myUnit) {
		units.add(myUnit);		
		myUnit.setSquad(this);
	}
	
	public void command(UnitState myCommand, Position pos, int range) {
		command = myCommand;
		objective = pos;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(myUnit.getUnit().exists()) {
				myUnit.command(myCommand, pos);
			} else {
				itr.remove();
			}
		}
	}
	public void holdChoke(Chokepoint choke) throws Exception {
		command = UnitState.HOLDING;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		center = findCenter();
		
		while(itr.hasNext()) {
			myUnit = itr.next();
			
			//merge excess archons
			if(GuiBot.enemyRace != Race.Terran && game.self().completedUnitCount(UnitType.Protoss_High_Templar) > 6) {
				for(MyUnit u: units) {
					if(!u.gotCommand && u.getUnit().getType() == UnitType.Protoss_High_Templar) {
						mergeArchon(u);
					}
				}
			}
			
			if(myUnit.getUnit().exists()) {
				myUnit.blockChoke(choke);
				game.drawCircleMap(myUnit.getPosition(), 16, Color.Red);
			} else {
				itr.remove();
			}
		}
	}
	public void attack(Position attackPosition, int range) throws Exception {
		objective = attackPosition;
		attack(range);
	}
	public void attack(int range) throws Exception {
		// TODO Auto-generated method stub
		command = UnitState.ATTACKING;
		radius = range;
		center = findCenter();
//		System.out.println(objective + " " + center);
		boolean attackBuildings = false;
		boolean canAttackAir = canAttackAir();
		for(Unit hisUnit: game.getUnitsInRadius(center, range)) {
			if(hisUnit.getPlayer() == game.enemy() && hisUnit.isDetected() && !hisUnit.isInvincible()
				&& hisUnit.getType() != UnitType.Resource_Vespene_Geyser 
				&& (hisUnit.isCompleted() || hisUnit.getType().isBuilding()|| hisUnit.getType() == UnitType.Zerg_Lurker_Egg)			
				&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva) {
				
				attackBuildings = true;
				if ((!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack() || hisUnit.getType() == UnitType.Terran_Bunker)
					&& (!hisUnit.isFlying() || canAttackAir)) {
					attackBuildings = false;
					break;
				}
			}				
		}

		MyUnit myUnit;
		
		//merge archons
		for(MyUnit u: units) {
			if(!u.gotCommand && u.getUnit().getType() == UnitType.Protoss_High_Templar && u.getUnit().getEnergy() < 75) {
				mergeArchon(u);
			}
		}
		
		Iterator<MyUnit> itr = units.iterator();
		if(isStaged(objective, range) || units.size() == 1 || attackBuildings) {// && objective.getApproxDistance(center) < 11*32)) {
			while(itr.hasNext()) {
				myUnit = itr.next();
				if(!myUnit.getUnit().exists()) {
					itr.remove();
				} else if(!myUnit.getUnit().isLoaded()) {
					myUnit.attack(objective, attackBuildings);
					if(attackBuildings)
						game.drawCircleMap(myUnit.getPosition(), 16, Color.Orange);
					else
						game.drawCircleMap(myUnit.getPosition(), 16, Color.Green);
				}
			}
		} else {
//			if(isTogether() && objective.getApproxDistance(center) < 17*32) {
				contain(objective, range);
//			} else {
//				groupUp();
//			}			
		}
//		game.drawCircleMap(objective,range, Color.Red);
//		game.drawLineMap(objective, center, Color.Green);
	}
	
	public void mergeArchon(MyUnit myUnit) {
		MyUnit closestTemplar = null;
		for(MyUnit otherUnit: units) {
			if(myUnit.getUnit().getLastCommand().getUnitCommandType() != UnitCommandType.Use_Tech_Unit
				&& otherUnit.getUnit().getLastCommand().getUnitCommandType() != UnitCommandType.Use_Tech_Unit
				&& otherUnit.getUnit().getType() == UnitType.Protoss_High_Templar && !otherUnit.equals(myUnit) && !otherUnit.gotCommand
				&& (otherUnit.getUnit().getEnergy() < 75 || game.self().completedUnitCount(UnitType.Protoss_High_Templar) > 6)) {
				
				if(closestTemplar == null || myUnit.getPosition().getApproxDistance(otherUnit.getPosition())
					< myUnit.getPosition().getApproxDistance(closestTemplar.getPosition())) {
					
					closestTemplar = otherUnit;
				}
			}
		}
		if(closestTemplar != null) {
			myUnit.getUnit().useTech(TechType.Archon_Warp, closestTemplar.getUnit());
			myUnit.setCommandGiven(true);
			closestTemplar.getUnit().useTech(TechType.Archon_Warp, myUnit.getUnit());
			game.drawLineMap(myUnit.getPosition(), closestTemplar.getPosition(), Color.Cyan);
			closestTemplar.setCommandGiven(true);
		}
	}
	
	public void contain(Position pos, int range) throws Exception {
		powerAtObjective = findPower(pos);
		command = UnitState.CONTAINING;
		objective = pos;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		double d;
		center = findCenter();
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(!myUnit.getUnit().exists()) {
				itr.remove();
			} else if(!myUnit.getUnit().isLoaded()) {
				d = myUnit.getPosition().getApproxDistance(pos);
				if(d >= range + 5*32) {
					myUnit.move(pos);
				} else if(myUnit.getTarget(false) != null) {
					if(myUnit.threatLevel() <= powerAtObjective) {
//						myUnit.attack(objective, false);
						myUnit.kiteBack(game.self().getStartLocation().toPosition());
						game.drawTextMap(myUnit.getPosition(),"  fite");
					} else {
						myUnit.moveAwayFrom(pos);
						//myUnit.move(game.self().getStartLocation().toPosition());
						game.drawTextMap(myUnit.getPosition(),"run");
					}
				} else if(d >= range+16) {
					myUnit.surround(pos, center, range);
				} else if(d >= range) {
					myUnit.getUnit().stop();
				} else
					myUnit.moveAwayFrom(pos);
				game.drawCircleMap(myUnit.getPosition(), 16, Color.Blue);
			}
		}
	}
	
	public double findPower(Position pos) {
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		double power = 0;
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(!myUnit.getUnit().exists()) {
				itr.remove();
			} else {
				double d = myUnit.getPosition().getApproxDistance(pos);
				int range = Math.max(myUnit.getUnit().getType().seekRange(), 
						game.self().weaponMaxRange(myUnit.getUnit().getType().groundWeapon()) + 32);
				
				if(range == 0 || !myUnit.getUnit().getType().canAttack()) {
					range = myUnit.getUnit().getType().sightRange();
				}
				power += 2*myUnit.getUnit().getType().supplyRequired()*range/d;
			}
		}
		return power;
	}
	
	public void resumeMining() {
		command = UnitState.MINING;
		UnitCommandType command = null;
		for(MyUnit myUnit: units) {
			command = myUnit.getUnit().getLastCommand().getUnitCommandType();
			if(command != UnitCommandType.Stop && (command == UnitCommandType.Attack_Move || command == UnitCommandType.Attack_Unit 
			|| command == UnitCommandType.Patrol || command == UnitCommandType.Hold_Position))
				myUnit.getUnit().stop();
		}
	}
	
	
	public void groupUp() throws Exception {
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		center = findCenter();
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(!myUnit.getUnit().exists()) {
				itr.remove();
			} else if(!myUnit.getUnit().isLoaded()) {
				if(myUnit.getPosition().getApproxDistance(center) >= 10*32-16
					&& myUnit.getUnit().hasPath(center)) {
//					&& GuiBot.walkMap[center.toTilePosition().getX()][center.toTilePosition().getY()] <= 0) {
					myUnit.move(center);
				} else {
					myUnit.move(objective);
				}
				game.drawCircleMap(myUnit.getPosition(),16, Color.White);
			}
		}
	}
	
	public Position findCenter() {
		unitTab = new HashMap<UnitType, Integer>();
		UnitType type = null;
		
		int centerX = 0;
		int centerY = 0;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		int units = 0;
		while(itr.hasNext()) {
			myUnit = itr.next();
			centerX += myUnit.getX();
			centerY += myUnit.getY();
			units++;
			type = myUnit.getUnit().getType();
			if(unitTab.containsKey(type)) {
				unitTab.put(type, unitTab.get(type) + 1);
			} else {
				unitTab.put(type, 1);
			}
		}
		if(units == 0) 
			return null;
		
		centerX /= units;
		centerY /= units;

		center = new Position(centerX, centerY);
//		game.drawCircleMap(new Position(centerX, centerY), 3, Color.Green);
//		game.drawTextMap(new Position(centerX, centerY), ""+units);
		
		return center;		
	}
	public ArrayList<MyUnit> getUnits() {
		return units;
	}
	public UnitState getCommand() {
		return command;
	}
	public Position getObjective() {
		return objective;
	}
	public boolean seesRangedEnemies() {
		return seesRangedEnemies;
	}
	public void setObjective(Position pos) {
		objective = pos;
	}
	public void takeUnit(MyUnit u, Iterator<MyUnit> itr) {
		units.add(u);
		u.setSquad(this);
		itr.remove();
	}
	public void takeAllUnits(Squad squad) {
		units.addAll(squad.getUnits());
		for(MyUnit u: squad.getUnits()) {
			u.setSquad(this);
		}
		squad.clearUnits();
	}
	public void removeUnit(MyUnit u) {
		units.remove(u);
	}
	public void clearUnits() {
		units.clear();
	}
	public boolean isTogether() {
		Position center = findCenter();
		for(MyUnit u: units) {
			if(u.getUnit().getPosition().getApproxDistance(center) >= 10*32) {
				return false;
			}
		}
		return true;
	}
	public boolean isStaged(Position pos, int range) {		
		Position center = findCenter();
		if(center != null && center.getApproxDistance(pos) < range+16)
			return true;

		for(MyUnit u: units) {
			if(u.getUnit().getPosition().getApproxDistance(pos) >= range + 5*32) {
				return false;
			}
		}		
		return true;
	}
	
	public int getUnitCount(UnitType type) {
		if(unitTab.containsKey(type))
			return unitTab.get(type);
		else
			return  0;
	}
	public int getUnitCount() {
		return units.size();
	}
	public void setSeesRangedEnemies(boolean rangedEnemyExists) {
		seesRangedEnemies = rangedEnemyExists;
	}
	public boolean canAttackAir() {
		for(MyUnit u: units) {
			if(u.getUnit().getType().airWeapon() != WeaponType.None)
				return true;
		}
		return false;
	}
}
