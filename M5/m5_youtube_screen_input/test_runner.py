"""
M5.1 Test Runner - Canonical: python test_runner.py
Also supports: python -m unittest discover -s tests -v
"""
import unittest
import sys
from pathlib import Path

def main():
    pkg_root = Path(__file__).parent
    project_root = pkg_root.parent
    # Ensure package root is in path
    sys.path.insert(0, str(project_root))
    sys.path.insert(0, str(pkg_root))
    loader = unittest.TestLoader()
    suite = loader.discover(str(pkg_root / "tests"), pattern="test_*.py")
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    # Summary breakdown
    print("\n" + "="*70)
    print("M5.1 TEST BREAKDOWN")
    print("="*70)
    print(f"Total tests run: {result.testsRun}")
    print(f"Failures: {len(result.failures)}")
    print(f"Errors: {len(result.errors)}")
    print(f"Skipped: {len(result.skipped)}")
    # Try to categorize from results
    # Note: Real vs Mock distinction is in test names - see TEST_RESULTS.md
    print("="*70)
    return result.wasSuccessful()

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
