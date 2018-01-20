import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class GuiBot extends DefaultBWListener {

    private Mirror mirror = new Mirror();
    
    private Game game;

    private Player self;

    private int supplydepot_x = 0;
    private int supplydepot_y = 0;
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        System.out.println("New unit discovered " + unit.getType());
    }

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }
        
        //player control enable
        game.enableFlag(1);
        
        supplydepot_x = self.getStartLocation().getX();
        supplydepot_y = self.getStartLocation().getY() + 3;
    }

    @Override
    public void onFrame() {
        //game.setTextSize(10);
        game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());

        StringBuilder units = new StringBuilder("My units:\n");

        //iterate through my units
        for (Unit myUnit : self.getUnits()) {
            units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");

            //if there's enough minerals, train a probe
            if (myUnit.getType() == UnitType.Protoss_Nexus && self.minerals() >= 50 && !myUnit.isTraining()) {
                myUnit.train(UnitType.Protoss_Probe);
            }
            
            if (myUnit.getType().isWorker() && self.minerals() >= 100) {
            	TilePosition temp = new TilePosition(supplydepot_x,supplydepot_y);
            	int tries = 0;
            	while(!game.canBuildHere(temp, UnitType.Protoss_Pylon, myUnit) && tries < 2) {
            		units.append(supplydepot_x + "\n");
            		supplydepot_x = self.getStartLocation().getX() + 2;
            		temp = new TilePosition(supplydepot_x,supplydepot_y);           
            		tries ++;		
            	}  
            	myUnit.build(UnitType.Protoss_Pylon, temp);
            }

            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker() && myUnit.isIdle()) {
                Unit closestMineral = null;

                //find the closest mineral
                for (Unit neutralUnit : game.neutral().getUnits()) {
                    if (neutralUnit.getType().isMineralField()) {
                        if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
                            closestMineral = neutralUnit;
                        }
                    }
                }

                //if a mineral patch was found, send the worker to gather it
                if (closestMineral != null) {
                    myUnit.gather(closestMineral, false);
                }
            }
        }

        //draw my units on screen
        game.drawTextScreen(10, 25, units.toString());
    }

    public static void main(String[] args) {
        new GuiBot().run();
    }
}

