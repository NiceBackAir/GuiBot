import java.util.ArrayList;

import bwapi.Game;
import bwapi.Position;
import bwta.BaseLocation;

public class HisBase {
	private ArrayList<HisUnit> turrets;
	private Game game;
	private int patchCount;
	private int workerCount;
	private BaseLocation base;
	
	public HisBase(Game game, BaseLocation baseLoc) {
		base = baseLoc;
		turrets = new ArrayList<HisUnit>();
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
	
	public void clearTurrets() {
		turrets.clear();
	}
	
	public ArrayList<HisUnit> getTurrets() {
		return turrets;
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
