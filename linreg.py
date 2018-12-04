# import libraries
# import libraries
import numpy as np
import random

class LinReg(object):
    # initialize
    # theta is the initial weights
    # alpha is the learning rate
    # iters is the number of iterations
    def __init__(self, theta = np.array([[1.0,1.0,1.0, 1.0]]), alpha = 0.0001, iters = 1000):
        self.theta = theta
        self.alpha = alpha
        self.iters = iters
        self.new_weights = "None"
        
    # compute cost function
    def computecost(self, X, y, theta):
        inner = np.power((np.matmul(X,theta.T)-y),2)
        return np.sum(inner)/(2*len(X))

    # compute the gradient and update the cost
    def gradientDescent(self, X, y):
        """
        X - attributes for the day being called at the end of the day
        y - actual step count at the end of the day
        """
        theta = self.theta
        for i in range(self.iters):
            theta = theta - (self.alpha / len(X)) * np.sum((X@theta.T - y)*X, axis = 0)
            cost = self.computecost(X, y, theta)
    
        self.new_weights = theta
        return theta, cost
        
    # get a prediction
    def predict(self, data):
        """
        data - tomorrow's min, max, humidity attributes
        """
        if(self.new_weights == "None"):
            return None
        
        return data.T @ self.new_weights[0]
        
        
# Demonstrate that your model update works
def main():
    # create some data 
    # need to create actual steps, weather, min, max, and temperature
    fake_steps = [1000.0, 5000.0, 9000.0, 11089.0, 150, 2150, 133, 12333, 17000, 10234]
    fake_min = [30, 56 , 67 ,68, 21]
    fake_max = [69, 70, 81, 90, 101]
    fake_humidity = [40,57,58,70, 95, 20, 65]

    store_x = []
    store_y = []
    for i in range(30):
        row = []
        row.append(1.0)
        row.append(random.choice(fake_max))
        row.append(random.choice(fake_min))
        row.append(random.choice(fake_humidity))
        store_x.append(row)
        store_y.append([random.choice(fake_steps)])
    
    data_x = np.array(store_x)
    data_y = np.array(store_y)
    

    # original_weights
    w_0 = 1215.23927059
    w_1 = 41.13028054
    w_2 = -11.34748777
    w_3 = 28.91362573

    # run a months worth of data
    print(data_x.shape)
    for i in range(data_x.shape[0]):
        # create the LinReg() object
        model = LinReg(theta = np.array([[w_0, w_1, w_2, w_3]]))
    
        # perform gradient descent and get new weights
        model.gradientDescent(data_x[i], data_y[i])
    
        # check the data
        print(model.predict(data_x[i]))
    
        # store new weights
        w_0 = model.new_weights[0][0]
        w_1 = model.new_weights[0][1]
        w_2 = model.new_weights[0][2]
        w_3 = model.new_weights[0][3]

if __name__ == '__main__':
    main()

