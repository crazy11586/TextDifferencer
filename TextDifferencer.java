package com.hcl.egit.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class TextDifferencer {

	private final List<String> left;
	private final List<String> right;
	private List<MatchingBlock> matches;
	private List<UnmatchedBlock> leftUnmatched;
	private List<UnmatchedBlock> rightUnmatched;
	private List<TextDiff> diffs;
	private int[][] lines;

	public TextDifferencer(String leftText, String rightText) {
		left = breakLines(leftText);
		right = breakLines(rightText);
		matches = new ArrayList<MatchingBlock>();
		leftUnmatched = new ArrayList<UnmatchedBlock>();
		rightUnmatched = new ArrayList<UnmatchedBlock>();

		int numLines = left.size() > right.size() ? left.size() : right.size();
		lines = new int[2][numLines];

		// Initially, both sides are completely unmatched
		leftUnmatched.add(new UnmatchedBlock(0, left.size()));
		rightUnmatched.add(new UnmatchedBlock(0, right.size()));
		doDiff();
	}

	public void doDiff() {

		// Start by looking for a match the size of the smaller text
		int numLines = left.size() > right.size() ? left.size() : right.size();

		// Start recursion with the single block
		while (numLines > 0) {
			findMatches(numLines);
			numLines--;
		}

		generateDiffs();
	}

	/**
	 * At the end of the differencing, the remaining unmatched lines are
	 * corresponding conflicts
	 */
	private void generateDiffs() {
		diffs = new ArrayList<TextDiff>();
		if (leftUnmatched.size() != rightUnmatched.size()) {
			System.out
					.println("leftUnmatched: " + leftUnmatched.size() + ", right unmatched: " + rightUnmatched.size()); //$NON-NLS-1$ //$NON-NLS-2$
		}

		for (int x = 0; x < leftUnmatched.size(); x++) {
			UnmatchedBlock leftBlock = leftUnmatched.get(x);
			UnmatchedBlock rightBlock = rightUnmatched.get(x);
			TextDiff diff = new TextDiff(leftBlock.getStartLine(), rightBlock.getStartLine(), leftBlock.getNumLines(),
					rightBlock.getNumLines());
			diffs.add(diff);
		}
	}

	private void findMatches(int numLines) {
		int leftIndex = getStartIndex('L', numLines);
		int rightIndex = getStartIndex('R', numLines);

		if (rightIndex == right.size()) {
			// the left side is matched
			return;
		}

		if (leftUnmatched == null || rightUnmatched == null)
			return;
		for (int x = 0; x + numLines <= left.size(); x++) {
			findMatchingRegion(x, leftUnmatched.get(x), numLines);
		}
	}

	private int getStartIndex(char c, int numLines) {
		int i;
		int length;
		switch (c) {
		case 'L':
			i = 0;
			length = left.size();
			break;
		case 'R':
			i = 1;
			length = right.size();
			break;
		default:
			i = -1;
			length = 0;
		}

		int precedingLeft = 0, precedingRight = 0;
		for (int x = 0; x < length - numLines; x++) {
			precedingLeft += lines[0][x];
			precedingRight += lines[1][x];
			if (lines[i][x] == 0 && (precedingLeft == precedingRight)) {
				int start = x;
				int sum = 0;
				for (int y = 0; y < numLines; y++) {
					sum += lines[i][x+y];
				}
				if(sum == 0)
					return x;
			}
		}

		return -1;

	}

	private MatchingBlock findMatchingRegion(int leftIndex, UnmatchedBlock leftBlock, int numLines) {
		// It can only match to the corresponding block on the right side of the text,
		// it wouldn't make sense if it could match up with text in another part of the
		// document for my purposes
		UnmatchedBlock rightBlock = rightUnmatched.get(0);

		if (rightBlock.getNumLines() < numLines) {
			// There aren't enough lines in the block to find a match of numLines length
			return null;
		}

		int startLine = rightBlock.getStartLine();
		MatchingBlock match;
		for (int x = 0; x + numLines <= leftBlock.getEndLine(); x++) {
			if ((match = hasMatch(x, numLines, rightBlock)) != null) {
				addMatchingBlock(match);
			}
		}
		return null;
	}

	private void addMatchingBlock(MatchingBlock match) {
		if (matches.size() == 0) {
			matches.add(match);
		} else {
			// Add it in sequential order
			for (int x = 0; x < matches.size(); x++) {
				if (matches.get(x).getLeftStartLine() > match.getLeftStartLine()) {
					matches.add(x, match);
					break;
				} else if (x == matches.size() - 1) {
					matches.add(match);
				}

			}
		}
	}

	private MatchingBlock hasMatch(int leftIndex, int numLines, UnmatchedBlock right) {
		for (int y = 0; y + numLines < right.getEndLine(); y++) {
			if (sequenceMatches(leftIndex, right.getStartLine() + y, numLines))
				return new MatchingBlock(leftIndex, right.getStartLine() + y, numLines);
		}

		return null;
	}

	private boolean sequenceMatches(int leftStart, int rightStart, int numLines) {

		for (int x = 0; x < numLines; x++) {
			if (!left.get(leftStart + x).equals(right.get(rightStart + x)))
				return false;
		}
		return true;
	}

	private List<String> breakLines(String text) {
		BufferedReader br = new BufferedReader(new StringReader(text));
		List<String> linesList = new ArrayList<String>();
		String line = ""; //$NON-NLS-1$
		try {
			while ((line = br.readLine()) != null) {
				linesList.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return linesList;
	}

	public List<TextDiff> getDiffs() {
		return diffs;
	}

	private class MatchingBlock {
		private int leftStartLine;
		private int rightStartLine;

		private int numLines;

		MatchingBlock(int leftStartLine, int rightStartLine, int numLines) {
			this.leftStartLine = leftStartLine;
			this.rightStartLine = rightStartLine;
			this.numLines = numLines;
		}

		public int getLeftStartLine() {
			return leftStartLine;
		}

		public int getRightStartLine() {
			return rightStartLine;
		}
	}

	private class UnmatchedBlock {
		private int startLine;
		private int numLines;

		UnmatchedBlock(int startLine, int numLines) {
			this.startLine = startLine;
			this.numLines = numLines;
		}

		public int getStartLine() {
			return startLine;
		}

		public int getNumLines() {
			return numLines;
		}

		public int getEndLine() {
			return startLine + numLines;
		}
	}

	public class TextDiff {
		private int leftStart;
		private int rightStart;
		private int leftLength;
		private int rightLength;

		TextDiff(int leftStart, int rightStart, int leftLength, int rightLength) {
			this.leftLength = leftStart;
			this.rightStart = rightStart;
			this.leftLength = leftLength;
			this.rightLength = rightLength;
		}

		public int getLeftStart() {
			return leftStart;
		}

		public int getRightStart() {
			return rightStart;
		}

		public int getLeftLength() {
			return leftLength;
		}

		public int getRightLength() {
			return rightLength;
		}

		public int getLeftEndLine() {
			return leftStart + leftLength;
		}

		public int getRightEndLine() {
			return rightStart + rightLength;
		}
	}

}
