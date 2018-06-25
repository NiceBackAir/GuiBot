import java.util.ArrayList;

import bwapi.Game;
import bwapi.Position;
import bwta.BaseLocation;

public class HisBase {
	private ArrayList<HisUnit> turrets;
	private ArrayList<HisUnit> defenders;
	private Game game;
	private int patchCount;
	private int workerCount;
	private BaseLocation base;
	
	public HisBase(Game game, BaseLocation baseLoc) {
		base = baseLoc;
		turrets = new ArrayList<HisUnit>();
		defenders = new ArrayList<HisUnit>();
		patchCount = 0;
		workerCount = 0;
	}
	
	public BaseLocation getBaseLocation() {
		return base;
	}
	
	public void update() {}
	
	public void addTurret(HisUnit hisUnit) {
		if(!turrets.contains(hisUnit)) {
			turrets.add(hisUnit);
		}
	}
	
	public void addDefender(HisUnit hisUnit) {
		if(!defenders.contains(hisUnit)) {
			defenders.add(hisUnit);
		}
	}
	
	public void clearTurrets() {
		turrets.clear();
	}
	
	public ArrayList<HisUnit> getTurrets() {
		return turrets;
	}
	
	public ArrayList<HisUnit> getDefenders() {
		return defenders;
	}
	
	public int getDefenderSupply() {
		int supply = 0;
		for(HisUnit u: defenders) {
			supply += u.getUnit().getType().supplyRequired();
		}
		
		return supply;
	}
	
	public Position getPosition() {
		return base.getPosition();
	}
	
	public void setWorkerCount() {
		
	}
	
	public void setPatchCount() {
		
	}
	
	public int getWorkerCount() {
		return workerCount;
	}
	
	public int getPatchCount() {
		return patchCount;
	}
}
