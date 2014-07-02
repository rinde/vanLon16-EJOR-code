library(data.table)
library(ggplot2)


filename <- "Central-Solver-CheapestInsertion"
dir <- "~/workspace/dyn-urg/files/results/"

# LOAD DATA
Central.Solver.CheapestInsertion <- read.csv(paste(dir,filename,".csv",sep=""),as.is=T,colClasses=list("numeric","numeric","numeric"))
dt1 <- data.table(Central.Solver.CheapestInsertion)
  
# TRANSFORM VALUES
dt2 <- dt1[,list(dynamism=round(10*dynamism)/10,
                 urgency=urgency_mean/60000,
                 cost )]
# GROUP
dt3 <- dt2[,list(cost=mean(cost)),by="dynamism,urgency"]

pdf(paste(dir,filename,".pdf",sep=""),height=6,width=8)   
# HEATMAP
p <- ggplot(dt3, aes(dynamism, urgency,fill=cost))  + geom_raster() + scale_fill_gradientn(
  colours=c("#ffffcc","#ffeda0","#fed976","#feb24c","#fd8d3c","#fc4e2a","#e31a1c","#bd0026","#800026"))
show(p)
 
dev.off()    

# 3D plot
#library(rgl)
#library(akima)

#s=interp(dt3$dynamism, 
#         dt3$urgency, 
#         dt3$cost)
#plot3d(dt3$dynamism, 
#       dt3$urgency, 
#       dt3$cost,
#       type='p',
#       xlab="dynamism",
#       ylab="urgency",
#       zlab="average cost")

#zlim <- range(s$z)
#zlen <- zlim[2] - zlim[1] + 1

#colorlut <- heat.colors(zlen) # height color lookup table
#col <- colorlut[ s$z-zlim[1]+1 ] # assign colors to heights for each point
#surface3d(s$x,s$y,s$z, color=col)

