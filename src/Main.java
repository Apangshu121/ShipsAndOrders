import java.util.*;
import java.util.concurrent.*;

class Order {
    int weight;
    String fromDestination;
    long orderTime;

    String toDestination;

    public Order(int weight, String fromDestination, String toDestination) {
        this.weight = weight;
        this.fromDestination = fromDestination;
        this.toDestination = toDestination;
        this.orderTime = System.currentTimeMillis();
    }

    public boolean isCanceled() {
        return System.currentTimeMillis() - orderTime > 60000;  // order is canceled if not picked up within a minute
    }
}

class Ship {
    int currentWeight;
    String currentLocation;
    int tripsMade;
    int totalCargo;

    public Ship(String currentLocation) {
        this.currentWeight = 0;
        this.currentLocation = currentLocation;
        this.tripsMade = 0;
        this.totalCargo = 0;
    }

    public void loadCargo(int weight) {
        this.currentWeight += weight;
        this.totalCargo += 1;
    }

    public void unloadCargo() {
        this.currentWeight = 0;
    }

    public void goForMaintenance() {
        this.tripsMade = 0;
    }
}

class WayneEnterprise {
    int totalRevenue;
    int totalOrders;
    int totalCanceledOrders;
    List<Ship> ships;
    BlockingQueue<Order> orders;

    public WayneEnterprise() {
        this.totalRevenue = 0;
        this.totalOrders = 0;
        this.totalCanceledOrders = 0;
        this.ships = new ArrayList<>();
        this.orders = new LinkedBlockingQueue<>();
    }

    public void placeOrder(Order order) throws InterruptedException {
        synchronized (this){
            orders.put(order);
            totalOrders++;
        }
    }

    public void executeOrder() throws InterruptedException {
        while (true) {
            Order order = orders.take();  // get the next order from the queue

            Ship ship = findShipAtLocation(order.fromDestination);

            if (ship == null) {
                synchronized (this){
                    orders.put(order);
                    continue;
                }
            }

            if (order.isCanceled()) {
                synchronized (this){
                    totalCanceledOrders++;
                    totalRevenue -= 250;
                    continue;
                }
            }

            synchronized (this) {
                ship.loadCargo(order.weight);
            }

            if (ship.currentWeight < 50) {
                continue;
            }

            synchronized (this){
                ship.currentLocation = order.toDestination;

                totalRevenue += (1000 * ship.totalCargo);
                ship.tripsMade++;
            }

            if (ship.tripsMade == 5) {
                ship.goForMaintenance();
                Thread.sleep(60000);  // 1 minute for maintenance
            }

            synchronized (this){
                if (totalRevenue >= 1000000) {
                    System.out.println("Total orders delivered: " + (totalOrders - totalCanceledOrders));
                    System.out.println("Total orders canceled: " + totalCanceledOrders);
                    System.exit(0);
                }
            }
        }
    }

    public Ship findShipAtLocation(String location) {
        for (Ship ship : ships) {
            if (ship.currentLocation.equals(location)) {
                return ship;
            }
        }
        return null;
    }
}

class Customer implements Runnable {
    WayneEnterprise enterprise;
    Random random;

    public Customer(WayneEnterprise enterprise) {
        this.enterprise = enterprise;
        this.random = new Random();
    }

    @Override
    public void run() throws RuntimeException {
        while (true) {
            int weight = 10 + random.nextInt(41);  // random weight between 10 and 50
            String fromDestination = random.nextBoolean() ? "Gotham" : "Atlanta";  // random destination
            String toDestination = "";

            if (fromDestination.equals("Gotham")) {
                toDestination = "Atlanta";
            } else {
                toDestination = "Gotham";
            }

            Order order = new Order(weight, fromDestination, toDestination);
            try {
                enterprise.placeOrder(order);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            try {
                Thread.sleep(5000);  // wait for 5 seconds before placing the next order
            } catch (InterruptedException e) {
                System.out.println(e);
            }
        }
    }
}

class Shipper implements Runnable {
    WayneEnterprise enterprise;

    public Shipper(WayneEnterprise enterprise) {
        this.enterprise = enterprise;
    }

    @Override
    public void run() {
        while (true) {
            try {
                enterprise.executeOrder();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

public class Main {
    public static void main(String[] args) {
        WayneEnterprise enterprise = new WayneEnterprise();

        for (int i = 0; i < 5; i++) {
            String location = i % 2 == 0 ? "Gotham" : "Atlanta";  // alternate starting location
            Ship ship = new Ship(location);
            enterprise.ships.add(ship);
            new Thread(new Shipper(enterprise)).start();
        }

        for (int i = 0; i < 7; i++) {
            new Thread(new Customer(enterprise)).start();
        }
    }
}