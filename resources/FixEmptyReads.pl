#!/usr/bin/perl

#this script reads a fastq.gz-file and changes empty reads to 'N' with quality '@'
use strict;
use warnings;
use Getopt::Long;
use FileHandle;


#perl FixEmptyReads.pl -o MyFastqWithNsInstead.fastq.gz <file || stdin>

my($input,$output);
GetOptions("o=s" => \$output);

# define filehandles for printing mates
open OUTPUT, "| gzip > $output" or die("cannot open file to write to. $!");

while(<>) {

    chomp;
    my $name = $_;
    my $seq = <>;
    chomp($seq);
    my $name2= <>;
    chomp($name2);
    my $qual = <>;
    chomp $qual;
    if ($seq eq ""){
	$seq = "N";
	$qual = "\@";
    }
    print OUTPUT "$name\n$seq\n$name2\n$qual\n";
}

close OUTPUT;

