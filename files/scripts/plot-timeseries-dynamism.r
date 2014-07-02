
dir <- "~/workspace/dyn-urg/files/results/time-series-dynamism-experiment/"

nonhomogTS <- read.table(paste(dir,"non-homog-poisson-dynamism.csv",sep=""), quote="\"",as.is=T,colClasses=list("numeric"))
homogTS <- read.table(paste(dir,"homog-poisson-dynamism.csv",sep=""), quote="\"",as.is=T,colClasses=list("numeric"))
normalTS <- read.table(paste(dir,"normal-dynamism.csv",sep=""), quote="\"",as.is=T,colClasses=list("numeric"))
uniformTS <- read.table(paste(dir,"uniform-dynamism.csv",sep=""), quote="\"",as.is=T,colClasses=list("numeric"))


nonhomogTS["type"] <- "non-homogeneous-Poisson"
homogTS["type"] <- "homogeneous-Poisson"
normalTS["type"] <- "normal"
uniformTS["type"] <- "uniform"


res <- merge(nonhomogTS,homogTS, all=T)
res <- merge(res,normalTS,all=T)
res <- merge(res,uniformTS,all=T)

df <- data.frame(res)

library(ggplot2)

p <- ggplot(df, aes(x=V1,fill=type)) + geom_histogram(binwidth=.01, alpha=.5, position="identity") + xlab("dynamism") + scale_x_continuous(breaks=seq(0, 1, 0.05)) + scale_fill_brewer(palette="Set1")
show(p)
