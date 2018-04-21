import java.util.ArrayList;
import java.util.Iterator;

import bwapi.Position;
import bwta.BaseLocation;
import bwta.Chokepoint;

public class Squad {
	private ArrayList<MyUnit> units;
	private UnitState command;
	private Position objective;
	private double radius;
	private BaseLocation base;
	
	public Squad() {
		units = new ArrayList<MyUnit>();
	}
	public Squad(ArrayList<MyUnit> myUnits) {
		units = myUnits;
	}
	
	public void add(MyUnit myUnit) {
		units.add(myUnit);		
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
		objective = choke.getCenter();
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(myUnit.getUnit().exists()) {
				myUnit.blockChoke(choke);
			} else {
				itr.remove();
			}
		}
	}
	public void attack(Position attackPosition) throws Exception {
		// TODO Auto-generated method stub
		command = UnitState.ATTACKING;
		objective = attackPosition;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(myUnit.getUnit().exists()) {
				myUnit.attack(attackPosition, true);
			} else {
				itr.remove();
			}
		}
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
	public boolean isTogether() {
		for(MyUnit u: units) {
			if(u.getUnit().getPosition().getApproxDistance(objective) >= radius) {
				return false;
			}
		}
		return true;
	}
}
