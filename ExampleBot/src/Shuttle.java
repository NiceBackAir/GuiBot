import bwapi.Game;
import bwapi.Unit;

public class Shuttle extends MyUnit {

	public Shuttle(Unit u, Game game) {
		super(u, game);
		// TODO Auto-generated constructor stub
	}

	
	public double[] dyingWishes() {
		if(u.canUnloadAtPosition(u.getPosition()))
			u.unloadAll(u.getPosition());
		else
			moveDownGradient();
		return null;
	}
}
