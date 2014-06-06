library(scatterplot3d)
library(data.table)

# LOAD DATA
Central.Solver.CheapestInsertion <- read.csv("~/workspace/dyn-urg/files/results/Central-Solver-CheapestInsertion.csv",as.is=T,colClasses=list("numeric","numeric","numeric"))
dt1 <- data.table(Central.Solver.CheapestInsertion)
  
# TRANSFORM VALUES
dt2 <- dt1[,list(dynamism=round(10*dynamism)/10,
                 urgency=urgency_mean/60000,
                 cost )]
# GROUP
dt3 <- dt2[,list(cost=mean(cost)),by="dynamism,urgency"]

# PLOT
scatterplot3d(dt3$dynamism, 
              dt3$urgency, 
              dt3$cost, 
              xlab="dynamism",
              ylab="urgency",
              zlab="average cost",
              main="3D Scatterplot", 
              sub="Plot of relation between dynamism and urgency and algorithm performance.",
              highlight.3d=TRUE)

#library(Rcmdr)
#scatter3d(dt3$dynamism, 
#          dt3$urgency, 
#          dt3$cost)

library(rgl)
library(akima)

s=interp(dt3$dynamism, 
         dt3$urgency, 
         dt3$cost)
plot3d(dt3$dynamism, 
       dt3$urgency, 
       dt3$cost,
       type='p',
       xlab="dynamism",
       ylab="urgency",
       zlab="average cost")

zlim <- range(s$z)
zlen <- zlim[2] - zlim[1] + 1

colorlut <- heat.colors(zlen) # height color lookup table

col <- colorlut[ s$z-zlim[1]+1 ] # assign colors to heights for each point

surface3d(s$x,s$y,s$z, color=col)


#attach(Central.Solver.CheapestInsertion)


#s3d <-scatterplot3d(dynamism, urgency_mean, cost, pch=16, highlight.3d=TRUE,
#                    type="h", main="3D Scatterplot")
#fit <- lm(cost ~ dynamism+urgency_mean) 
#s3d$plane3d(fit)

#persp()

#library(rgl)




#dtf <- data.frame(Central.Solver.CheapestInsertion)
#str(dtf["dynamism"])


#str(res2$dynamism)


#plot3d(dynamism, urgency, cost,  size=3)
#library(plot3D)
#surf3D(dynamism,urgency,cost)